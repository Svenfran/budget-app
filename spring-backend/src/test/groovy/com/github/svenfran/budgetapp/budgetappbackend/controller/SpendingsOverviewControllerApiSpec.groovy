package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static java.nio.charset.StandardCharsets.UTF_8
import static java.time.LocalDate.of
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * API-Spec der Spending-Übersicht: GET /api/spendings/{groupId} (Gesamtübersicht) und
 * GET /api/spendings/{groupId}/{year} (Jahresübersicht).
 *
 * DIV-33 – Berechtigungen und DTO-Vertrag:
 * - Ohne Anmeldung 403 (Ist-Verhalten von Spring Security, nicht 401), Nutzer ohne Gruppenzugehörigkeit 403,
 *   unbekannte Gruppe 404, Owner und Mitglied 200. Die Regel selbst testet VerificationServiceSpec.
 * - Die JSON-Struktur wird gegen die Frontend-Modelle in ionic-frontend/src/app/domains/overview/model/ geprüft
 *   (Jahresübersicht: SpendingsOverviewDto, Gesamtübersicht: SpendingsOverviewYearlyDto): Jedes Modellfeld ist
 *   vorhanden und hat den passenden JSON-Typ. Bewusst keine exakte Schlüsselmenge: Felder ohne Frontend-Gegenstück
 *   (groupId, die jeweils nicht befüllte Liste als null) sollen additive Backend-Änderungen nicht brechen.
 * - Die Gesamtübersicht liefert year = 0 (nicht gesetzt); das Frontend liest es nicht, geprüft wird nur der Typ.
 * - Die Berechnung testen SpendingsOverviewServiceSpec und SpendingsOverviewServiceIntegrationSpec.
 *
 * - Logging der Struktur-Features: INFO je Feature eine Zeile Szenario, eine je Request (Status) und eine Zusammenfassung
 *   der geprüften Antwort; den vollständigen JSON-Body nur auf DEBUG
 *   (logging.level.com.github.svenfran.budgetapp.budgetappbackend.controller=DEBUG).
 *
 * DIV-30: monthName der Jahresübersicht folgt der Sprache aus Accept-Language (de/en/es, Standard de).
 * Das Frontend zeigt monthName nicht an (eigene MonthNamePipe), das Feld ist aber Teil des DTO-Vertrags.
 */
class SpendingsOverviewControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(SpendingsOverviewControllerApiSpec)

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

    def "Jahresübersicht enthält die Felder des Frontend-Modells SpendingsOverviewDto"() {
        given: 'Owner zahlt im März, Ben im April 2025'
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben], memberSince: of(2025, 1, 1))
        fixtures.cart(wg, amount: 30d, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        fixtures.cart(wg, user: ben, amount: 20d, datePurchased: DateUtils.toDate(of(2025, 4, 10)))
        def (ownerName, benName) = [wg.owner.name, ben.name]
        def groupId = wg.group.id
        def owner = wg.owner
        flushAndClear()
        logSzenario(groupId, owner.id, ben.id, 'Einkäufe 2025-03-10 Owner 30.00, 2025-04-10 Ben 20.00')

        and: 'Feldnamen und JSON-Typen aus ionic-frontend/src/app/domains/overview/model/'
        def overviewFelder = [year: Number, spendingsTotalYear: Map, spendingsPerMonth: List, availableYears: List]
        def totalFelder = [sumTotalYear: Number, spendingsTotalUser: List]
        def monatFelder = [month: Number, monthName: String, sumTotalMonth: Number, spendingsMonthlyUser: List]
        def userFelder = [userId: Number, userName: String, sum: Number, diff: Number]
        def passt = { objekt, Map felder -> felder.every { name, typ -> objekt.containsKey(name) && typ.isInstance(objekt[name]) } }

        when:
        def response = send(spendings(groupId, 2025), user: owner)
        logAntwort("GET /api/spendings/$groupId/2025 als Owner", response)
        def body = json(response)
        def totalUser = body.spendingsTotalYear?.spendingsTotalUser
        def monatsUser = body.spendingsPerMonth?.collectMany { it.spendingsMonthlyUser ?: [] }
        LOG.info('Antwort: Felder {}, Monate {}, User-Einträge Jahressumme {} / Monate {}',
                body.keySet(), body.spendingsPerMonth*.month, totalUser?.size(), monatsUser?.size())

        then:
        response.status == 200
        passt(body, overviewFelder)
        passt(body.spendingsTotalYear, totalFelder)
        !body.spendingsPerMonth.isEmpty()
        body.spendingsPerMonth.every { passt(it, monatFelder) }

        and: 'User-Einträge aus Jahressumme und allen Monaten, keine Liste leer'
        !totalUser.isEmpty()
        body.spendingsPerMonth.every { !it.spendingsMonthlyUser.isEmpty() }
        (totalUser + monatsUser).every { passt(it, userFelder) }

        and: 'Werte, die das Frontend anzeigt; Monate absteigend (overview.page.html liest erstes/letztes Element)'
        body.year == 2025
        body.availableYears == [2025]
        body.spendingsPerMonth*.month == [4, 3]
        totalUser*.userName as Set == [ownerName, benName] as Set
    }

    def "Gesamtübersicht enthält die Felder des Frontend-Modells SpendingsOverviewYearlyDto"() {
        given: 'Owner zahlt im März, Ben im April 2025'
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben], memberSince: of(2025, 1, 1))
        fixtures.cart(wg, amount: 30d, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        fixtures.cart(wg, user: ben, amount: 20d, datePurchased: DateUtils.toDate(of(2025, 4, 10)))
        def (ownerName, benName) = [wg.owner.name, ben.name]
        def groupId = wg.group.id
        def owner = wg.owner
        flushAndClear()
        logSzenario(groupId, owner.id, ben.id, 'Einkäufe 2025-03-10 Owner 30.00, 2025-04-10 Ben 20.00')

        and: 'Feldnamen und JSON-Typen aus ionic-frontend/src/app/domains/overview/model/'
        def overviewFelder = [year: Number, spendingsTotalYear: Map, spendingsPerYear: List, availableYears: List]
        def totalFelder = [sumTotalYear: Number, spendingsTotalUser: List]
        def jahrFelder = [year: Number, sumTotalYear: Number, spendingsYearlyUser: List]
        def userFelder = [userId: Number, userName: String, sum: Number, diff: Number]
        def passt = { objekt, Map felder -> felder.every { name, typ -> objekt.containsKey(name) && typ.isInstance(objekt[name]) } }

        when:
        def response = send(spendings(groupId, null), user: owner)
        logAntwort("GET /api/spendings/$groupId als Owner", response)
        def body = json(response)
        def totalUser = body.spendingsTotalYear?.spendingsTotalUser
        def jahresUser = body.spendingsPerYear?.collectMany { it.spendingsYearlyUser ?: [] }
        LOG.info('Antwort: Felder {}, Jahre {}, User-Einträge Gesamtsumme {} / Jahre {}',
                body.keySet(), body.spendingsPerYear*.year, totalUser?.size(), jahresUser?.size())

        then:
        response.status == 200
        passt(body, overviewFelder)
        passt(body.spendingsTotalYear, totalFelder)
        !body.spendingsPerYear.isEmpty()
        body.spendingsPerYear.every { passt(it, jahrFelder) }

        and: 'User-Einträge aus Gesamtsumme und allen Jahren, keine Liste leer'
        !totalUser.isEmpty()
        body.spendingsPerYear.every { !it.spendingsYearlyUser.isEmpty() }
        (totalUser + jahresUser).every { passt(it, userFelder) }

        and: 'Werte, die das Frontend anzeigt'
        body.availableYears == [2025]
        body.spendingsPerYear*.year == [2025]
        totalUser*.userName as Set == [ownerName, benName] as Set
    }

    def "Ohne Einkäufe liefern beide Endpoints leere Listen statt null"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        def owner = wg.owner
        flushAndClear()
        logSzenario(groupId, owner.id, null, 'keine Einkäufe')

        when:
        def gesamt = send(spendings(groupId, null), user: owner)
        logAntwort("GET /api/spendings/$groupId als Owner", gesamt)
        def jahr = send(spendings(groupId, 2025), user: owner)
        logAntwort("GET /api/spendings/$groupId/2025 als Owner", jahr)
        def gesamtBody = json(gesamt)
        def jahrBody = json(jahr)
        LOG.info('Antwort: Gesamtübersicht spendingsPerYear={}, spendingsTotalUser={}, availableYears={}; ' +
                'Jahresübersicht spendingsPerMonth={}, spendingsTotalUser={}, availableYears={}',
                gesamtBody.spendingsPerYear, gesamtBody.spendingsTotalYear?.spendingsTotalUser, gesamtBody.availableYears,
                jahrBody.spendingsPerMonth, jahrBody.spendingsTotalYear?.spendingsTotalUser, jahrBody.availableYears)

        then:
        gesamt.status == 200
        gesamtBody.spendingsPerYear == []
        gesamtBody.spendingsTotalYear.spendingsTotalUser == []
        gesamtBody.spendingsTotalYear.sumTotalYear == 0
        gesamtBody.availableYears == []

        and:
        jahr.status == 200
        jahrBody.spendingsPerMonth == []
        jahrBody.spendingsTotalYear.spendingsTotalUser == []
        jahrBody.spendingsTotalYear.sumTotalYear == 0
        jahrBody.availableYears == []
    }

    /** Feature-Name und Szenario (INFO), damit sich die folgenden Zeilen einem Feature zuordnen lassen. */
    private void logSzenario(Long groupId, Long ownerId, Long memberId, String daten) {
        LOG.info('>> {}', specificationContext.currentIteration.displayName)
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {}, {}', groupId, ownerId, memberId ?: '-', daten)
    }

    /** Request und Status (INFO), der vollständige Body nur auf DEBUG. Bytes als UTF-8, siehe ApiRequests.json. */
    private static void logAntwort(String request, MockHttpServletResponse response) {
        LOG.info('{} -> {}', request, response.status)
        LOG.debug('Body: {}', new String(response.contentAsByteArray, UTF_8))
    }

    /** Ohne year die Gesamtübersicht über alle Jahre, sonst die Jahresübersicht. */
    private static MockHttpServletRequestBuilder spendings(Long groupId, Integer year) {
        year == null
                ? get('/api/spendings/{groupId}', groupId)
                : get('/api/spendings/{groupId}/{year}', groupId, year)
    }
}
