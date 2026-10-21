package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static java.time.LocalDate.of
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * DIV-30: monthName der Jahresübersicht folgt der Sprache aus Accept-Language (de/en/es, Standard de).
 * Das Frontend zeigt monthName nicht an (eigene MonthNamePipe), das Feld ist aber Teil des DTO-Vertrags.
 */
class SpendingsOverviewControllerApiSpec extends IntegrationSpec implements ApiRequests {

    @Autowired ObjectMapper objectMapper

    def "Accept-Language '#header' liefert den Monatsnamen '#expected'"() {
        given:
        def wg = fixtures.group(memberSince: of(2025, 1, 1))
        fixtures.cart(wg, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        flushAndClear()

        when:
        def request = get('/api/spendings/{groupId}/{year}', wg.group.id, 2025)
        if (header) {
            request.header('Accept-Language', header)
        }
        def response = send(request, user: wg.owner)

        then:
        response.status == 200
        def body = json(response)
        body.spendingsPerMonth*.month == [3]
        body.spendingsPerMonth*.monthName == [expected]

        where:
        header  || expected
        'en'    || 'March'
        'es'    || 'marzo'
        'en-US' || 'March'
        null    || 'März'
    }

    def "#endpoint: ohne Anmeldung wird der Zugriff mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(spendings(wg.group.id, year)).status == 403

        where:
        endpoint          | year
        'Gesamtübersicht' | null
        'Jahresübersicht' | 2025
    }

    def "#endpoint: angemeldeter Nutzer ohne Gruppenzugehörigkeit erhält 403"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()

        expect:
        send(spendings(wg.group.id, year), user: fremder).status == 403

        where:
        endpoint          | year
        'Gesamtübersicht' | null
        'Jahresübersicht' | 2025
    }

    def "#endpoint: unbekannte Gruppe liefert 404"() {
        given:
        def angemeldet = fixtures.user()

        when: 'Long.MAX_VALUE statt einer kleinen ID, die Restdaten aus Commit-Specs treffen könnte'
        def response = send(spendings(Long.MAX_VALUE, year), user: angemeldet)

        then:
        response.status == 404
        response.contentAsString.contains('not found')

        where:
        endpoint          | year
        'Gesamtübersicht' | null
        'Jahresübersicht' | 2025
    }

    def "#endpoint: #rolle erhält 200"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben], memberSince: of(2025, 1, 1))
        def groupId = wg.group.id
        def angemeldet = rolle == 'Owner' ? wg.owner : ben
        flushAndClear()

        when:
        def response = send(spendings(groupId, year), user: angemeldet)

        then:
        response.status == 200
        json(response).groupId == groupId

        where:
        endpoint          | year | rolle
        'Gesamtübersicht' | null | 'Owner'
        'Gesamtübersicht' | null | 'Mitglied'
        'Jahresübersicht' | 2025 | 'Owner'
        'Jahresübersicht' | 2025 | 'Mitglied'
    }

    /** Ohne year die Gesamtübersicht über alle Jahre, sonst die Jahresübersicht. */
    private static MockHttpServletRequestBuilder spendings(Long groupId, Integer year) {
        year == null
                ? get('/api/spendings/{groupId}', groupId)
                : get('/api/spendings/{groupId}/{year}', groupId, year)
    }
}
