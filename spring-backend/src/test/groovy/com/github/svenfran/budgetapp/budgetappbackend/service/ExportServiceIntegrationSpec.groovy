package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import java.time.LocalDate

import static java.time.LocalDate.of

/**
 * Ausgabenexport (DIV-68): Datenaufbereitung und Gruppenauswahl über ExportService.loadExportData. Die Formate (XLSX,
 * CSV) prüfen die Writer-Specs; hier geht es um Berechtigung, Gruppenauswahl und die fachlichen Zeilen.
 *
 * - Ohne Request liest der Translator die JVM-Locale: Features mit übersetztem Text setzen Locale.GERMAN und setzen
 *   sie im cleanup:-Block zurück.
 * - Bedingungen enthalten nur IDs und Zeilenwerte (Records), keine Entities.
 */
class ExportServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ExportServiceIntegrationSpec)
    private static final LocalDate SEIT = of(2024, 1, 1)

    @Autowired ExportService exportService

    def "Einzelgruppe: alle Carts inkl. Ausgleichszahlung und gelöschtem Nutzer, absteigend nach Kaufdatum, mit vollständiger Historie"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def geloescht = fixtures.user(name: 'DELETED')
        def wg = fixtures.group(owner: anna, members: [ben, geloescht], memberSince: SEIT)
        def cleo = fixtures.user(name: 'Cleo')
        fixtures.membership(wg, cleo, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2024, 6, 30)))
        einkauf(wg, anna, 'Pizza', 30d, of(2025, 3, 1))
        einkauf(wg, ben, 'Ausgleich an Anna', 20d, of(2025, 5, 1))
        einkauf(wg, anna, 'Ausgleich von Ben', -20d, of(2025, 5, 1))
        einkauf(wg, geloescht, 'Kino', 12d, of(2024, 2, 1))
        flushAndClear()
        loginAs(anna)
        def gid = wg.group.id
        LOG.info('Szenario: Locale de, Gruppe {} "{}", Owner Anna {}, Mitglieder Ben {}, gelöschter Nutzer {}, Cleo {} ' +
                '(Mitgliedschaft bis 30.06.2024); 4 Carts inkl. Ausgleich +20/-20', gid, wg.group.name, anna.id, ben.id,
                geloescht.id, cleo.id)

        when:
        def result = exportService.loadExportData(gid)
        def export = result[0]
        LOG.info('exportService.loadExportData({}) -> {} Gruppe(n), {} Carts {}, {} Mitgliedschaften {}', gid, result.size(),
                export.carts().size(), export.carts()*.title(), export.memberships().size(), export.memberships()*.userName())

        then:
        result*.groupId() == [gid]
        export.groupName() == wg.group.name

        and: "Carts absteigend nach Kaufdatum, Ausgleichszahlung mit negativem Betrag, gelöschter Nutzer übersetzt"
        export.carts()*.datePurchased() == [of(2025, 5, 1), of(2025, 5, 1), of(2025, 3, 1), of(2024, 2, 1)]
        export.carts()*.amount() as Set == [20d, -20d, 30d, 12d] as Set
        def kino = export.carts().find { it.title() == 'Kino' }
        kino.userName() == 'Nutzer gelöscht'
        kino.category() == wg.category.name
        kino.groupName() == wg.group.name
        kino.description() == 'Wocheneinkauf'

        and: "Historie vollständig, beendete Mitgliedschaft mit Enddatum, offene ohne"
        export.memberships().size() == 4
        def cleoRow = export.memberships().find { it.userName() == 'Cleo' }
        cleoRow.start() == SEIT
        cleoRow.end() == of(2024, 6, 30)
        def annaRow = export.memberships().find { it.userName() == 'Anna' }
        annaRow.end() == null
        annaRow.type() == TypeEnum.OWNER.toString()
        export.memberships()*.userName() as Set == ['Anna', 'Ben', 'Nutzer gelöscht', 'Cleo'] as Set

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Alle Gruppen: nur Gruppen des Nutzers, jede genau einmal, nach ID sortiert; fremde und verlassene Gruppen fehlen"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, members: [anna, fixtures.user(name: 'Ben')], memberSince: SEIT)
        def urlaub = fixtures.group(members: [anna], memberSince: SEIT)
        def fremd = fixtures.group(memberSince: SEIT)
        def frueher = fixtures.group(memberSince: SEIT)
        fixtures.membership(frueher, anna, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2025, 1, 31)))
        einkauf(haushalt, anna, 'Pizza', 30d, of(2025, 3, 1))
        einkauf(urlaub, anna, 'Hotel', 300d, of(2025, 8, 1))
        einkauf(fremd, fremd.owner, 'Fremd', 99d, of(2025, 8, 1))
        einkauf(frueher, anna, 'Früher', 50d, of(2025, 1, 10))
        flushAndClear()
        loginAs(anna)
        def (haushaltId, urlaubId, fremdId, frueherId) = [haushalt.group.id, urlaub.group.id, fremd.group.id, frueher.group.id]
        LOG.info('Szenario: Anna {}; Haushalt {} (Owner und Mitglied), Urlaub {} (Mitglied), fremd {}, früher {} (entfernt)',
                anna.id, haushaltId, urlaubId, fremdId, frueherId)

        when:
        def result = exportService.loadExportData(null)
        LOG.info('exportService.loadExportData(null) -> Gruppen {}, Carts {}', result*.groupId(), result.collect { it.carts()*.title() })

        then:
        result*.groupId() == [haushaltId, urlaubId]
        result.collect { it.carts()*.title() } == [['Pizza'], ['Hotel']]
    }

    def "Nicht-Mitglied mit groupId einer fremden Gruppe erhält NotOwnerOrMemberOfGroupException"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def fremd = fixtures.group(memberSince: SEIT)
        flushAndClear()
        loginAs(anna)
        def fremdId = fremd.group.id
        LOG.info('Szenario: Anna {} ist nicht in Gruppe {} (Owner {})', anna.id, fremdId, fremd.owner.id)

        when:
        LOG.info('exportService.loadExportData({}) als User {}, erwartet NotOwnerOrMemberOfGroupException', fremdId, anna.id)
        exportService.loadExportData(fremdId)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
    }

    def "Unbekannte groupId ergibt GroupNotFoundException"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        flushAndClear()
        loginAs(anna)
        def unbekannt = Long.MAX_VALUE
        LOG.info('Szenario: Anna {}, Gruppe {} existiert nicht', anna.id, unbekannt)

        when:
        LOG.info('exportService.loadExportData({}), erwartet GroupNotFoundException', unbekannt)
        exportService.loadExportData(unbekannt)

        then:
        thrown(GroupNotFoundException)
    }

    def "Nutzer ohne Gruppen erhält bei Alle Gruppen eine leere Liste"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {} ohne Gruppen', anna.id)

        when:
        def result = exportService.loadExportData(null)
        LOG.info('exportService.loadExportData(null) -> {} Gruppen', result.size())

        then:
        result.isEmpty()
    }

    def "Betrag mit mehr als zwei Nachkommastellen wird als gespeicherter Rohwert übernommen"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def wg = fixtures.group(owner: anna, memberSince: SEIT)
        einkauf(wg, anna, 'Krumm', 12.345d, of(2025, 3, 1))
        flushAndClear()
        loginAs(anna)
        def gid = wg.group.id
        LOG.info('Szenario: Gruppe {}, Anna {} kauft für 12.345', gid, anna.id)

        when:
        def amount = exportService.loadExportData(gid)[0].carts()[0].amount()
        LOG.info('exportService.loadExportData({}) -> Betrag {}', gid, amount)

        then:
        amount == 12.345d
    }

    private void einkauf(GroupScenario scenario, user, String title, double amount, LocalDate date) {
        fixtures.cart(scenario, user: user, title: title, amount: amount, averagePerMember: amount,
                datePurchased: DateUtils.toDate(date))
    }
}
