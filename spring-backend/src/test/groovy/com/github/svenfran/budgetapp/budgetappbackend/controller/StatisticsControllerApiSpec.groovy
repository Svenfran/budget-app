package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static java.time.LocalDate.of
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * API-Spec der Statistikseite (DIV-63): GET /api/statistics?period=…[&groupId][&year][&month].
 *
 * - JSON-Form des StatisticsDto (Frontend-Modell statistics/model/statistics-dto.ts): Felder, Typen, settlement als
 *   Boolean, from/to als ISO-Datum (yyyy-MM-dd).
 * - Ohne Anmeldung 403 (Ist-Verhalten von Spring Security), fremde Gruppe 403, unbekannte Gruppe 404.
 * - Ungültige Parameterkombinationen 400 statt 500, auch technisch nicht darstellbare Jahre; keine fachliche
 *   Jahresgrenze.
 * - Die Berechnung testen StatisticsCalculatorSpec und StatisticsServiceIntegrationSpec.
 */
class StatisticsControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsControllerApiSpec)

    @Autowired ObjectMapper objectMapper

    def "MONTH: Antwort enthält Kennzahlen, ISO-Wochen mit Datum als yyyy-MM-dd und Kategorien mit settlement"() {
        given: 'März 2025 beginnt an einem Samstag: KW 9 ist auf 01.–02.03. beschnitten'
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben], memberSince: of(2024, 1, 1))
        def lebensmittel = fixtures.category(wg, name: 'Lebensmittel')
        fixtures.cart(wg, category: lebensmittel, amount: 12.5d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        fixtures.cart(wg, user: ben, amount: 7.5d, datePurchased: DateUtils.toDate(of(2025, 3, 20)))
        def groupId = wg.group.id
        def owner = wg.owner
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {} zahlt 12.50 (Lebensmittel, 2025-03-01), Ben {} 7.50 (2025-03-20)',
                groupId, owner.id, ben.id)

        when:
        def response = send(statistics(groupId: groupId, period: 'MONTH', year: 2025, month: 3), user: owner)
        def body = json(response)
        LOG.info('Antwort: Felder {}, totalSpent {}, paidByUser {}, paidCartCount {}, currentBalance {}, Wochen {}, Kategorien {}',
                body.keySet(), body.totalSpent, body.paidByUser, body.paidCartCount, body.currentBalance, body.timeline*.week,
                body.categories)

        then:
        response.status == 200
        body.keySet() == ['groupId', 'period', 'year', 'month', 'totalSpent', 'paidByUser', 'paidCartCount',
                          'currentBalance', 'availableYears', 'timeline', 'categories'] as Set
        body.groupId == groupId
        body.period == 'MONTH'
        body.year == 2025
        body.month == 3
        body.totalSpent == 20.0
        body.paidByUser == 12.5
        body.paidCartCount == 1
        body.currentBalance == 2.5
        body.availableYears == [2025]

        and:
        body.timeline*.week == [9, 10, 11, 12, 13, 14]
        body.timeline[0] == [year: 2025, month: null, week: 9, from: '2025-03-01', to: '2025-03-02', amount: 12.5]
        body.timeline[-1] == [year: 2025, month: null, week: 14, from: '2025-03-31', to: '2025-03-31', amount: 0.0]
        body.categories == [[name: 'Lebensmittel', amount: 12.5, settlement: false]]
    }

    def "YEAR ohne groupId: alle Gruppen, groupId null, 12 Monatseinträge ohne Wochenfelder"() {
        given:
        def wg = fixtures.group(memberSince: of(2024, 1, 1))
        fixtures.cart(wg, amount: 40d, datePurchased: DateUtils.toDate(of(2025, 2, 10)))
        def owner = wg.owner
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {} zahlt 40 am 2025-02-10; Abfrage ohne groupId', wg.group.id, owner.id)

        when:
        def response = send(statistics(period: 'YEAR', year: 2025), user: owner)
        def body = json(response)
        LOG.info('Antwort: groupId {}, month {}, Monate {}, Beträge {}', body.groupId, body.month,
                body.timeline*.month, body.timeline*.amount)

        then:
        response.status == 200
        body.groupId == null
        body.month == null
        body.timeline*.month == (1..12).toList()
        body.timeline[1] == [year: 2025, month: 2, week: null, from: null, to: null, amount: 40.0]
    }

    def "Ohne Anmeldung wird der Zugriff mit 403 abgelehnt"() {
        given:
        LOG.info('Szenario: Request ohne Anmeldung')

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(statistics(period: 'ALL')).status == 403
    }

    def "Angemeldeter Nutzer ohne Gruppenzugehörigkeit erhält für die Gruppe 403"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        LOG.info('Szenario: Gruppe {}, Owner {}, Fremder {} (kein Mitglied)', wg.group.id, wg.owner.id, fremder.id)

        expect:
        send(statistics(groupId: wg.group.id, period: 'ALL'), user: fremder).status == 403
    }

    def "Unbekannte Gruppe liefert 404"() {
        given:
        def angemeldet = fixtures.user()
        LOG.info('Szenario: angemeldeter User {}, Gruppe {} existiert nicht', angemeldet.id, Long.MAX_VALUE)

        when: 'Long.MAX_VALUE statt einer kleinen ID, die Restdaten aus Commit-Specs treffen könnte'
        def response = send(statistics(groupId: Long.MAX_VALUE, period: 'ALL'), user: angemeldet)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        response.contentAsString.contains('not found')
    }

    def "Ungültige Parameter (#fall) liefern 400"() {
        given:
        def angemeldet = fixtures.user()
        LOG.info('Szenario: angemeldeter User {}, Parameter {}', angemeldet.id, parameter)

        when:
        def response = send(statistics(parameter), user: angemeldet)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 400

        where:
        fall                          | parameter
        'YEAR ohne year'              | [period: 'YEAR']
        'MONTH ohne year'             | [period: 'MONTH', month: 3]
        'MONTH ohne month'            | [period: 'MONTH', year: 2025]
        'month 0'                     | [period: 'MONTH', year: 2025, month: 0]
        'month 13'                    | [period: 'MONTH', year: 2025, month: 13]
        'unbekannte period'           | [period: 'FOO']
        'period fehlt'                | [year: 2025]
        'year nicht darstellbar YEAR' | [period: 'YEAR', year: Integer.MAX_VALUE]
        'year nicht darstellbar MONTH'| [period: 'MONTH', year: Integer.MAX_VALUE, month: 1]
        'year am LocalDate-Rand MONTH'| [period: 'MONTH', year: 999_999_999, month: 12]
        'year am LocalDate-Rand YEAR' | [period: 'YEAR', year: -999_999_999]
    }

    private static MockHttpServletRequestBuilder statistics(Map parameter) {
        def request = get('/api/statistics')
        parameter.each { name, wert -> request.param(name as String, wert as String) }
        request
    }
}
