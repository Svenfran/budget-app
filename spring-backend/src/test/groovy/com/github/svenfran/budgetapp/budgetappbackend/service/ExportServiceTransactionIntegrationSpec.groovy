package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.ExportFormat
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import static java.time.LocalDate.of

/**
 * Ausgabenexport (DIV-68) als Commit-Spec: loadExportData muss ihre Transaktion selbst öffnen. Die
 * Berechtigungsprüfung vergleicht Nutzer per Identität (Entities ohne equals) und liest dazu die Lazy-Collection
 * Group.members — beides geht nur in einem gemeinsamen Persistence Context. Ohne @Transactional an der Methode fiele das
 * hier als LazyInitializationException auf; die Rollback-Spec kann das nicht zeigen, weil sie die Transaktion stellt.
 * Nachweis trägt die Iteration „Einzelgruppe“ (gegengeprüft); „Alle Gruppen“ liest heute keine Lazy-Referenz und
 * sichert nur ab, dass dieser Weg ohne äußere Transaktion funktioniert.
 * Dasselbe gilt für export(), den Einstieg des Endpoints: Sein Aufruf von loadExportData() ist ein Selbstaufruf am
 * Proxy vorbei, die Transaktion muss daher von export() selbst kommen.
 * Kein flushAndClear(): ohne Test-Transaktion committet jeder Repository-Aufruf selbst.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExportServiceTransactionIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ExportServiceTransactionIntegrationSpec)

    @Autowired ExportService exportService
    @Autowired DatabaseCleaner databaseCleaner

    def setup() {
        databaseCleaner.truncateAll()
    }

    def "loadExportData prüft die Berechtigung und liest die Carts ohne äußere Transaktion (#umfang)"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben])
        fixtures.cart(wg, title: 'Pizza', amount: 30d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        loginAs(ben)
        def gid = wg.group.id
        def (gruppenName, kategorieName) = [wg.group.name, wg.category.name]
        LOG.info('Szenario: Gruppe {} "{}", Owner Anna {}, Mitglied Ben {} (angemeldet), Cart Pizza von Anna in Kategorie ' +
                '"{}" (committet)', gid, gruppenName, anna.id, ben.id, kategorieName)

        when:
        def result = exportService.loadExportData(mitGroupId ? gid : null)
        def rows = result[0].carts()
        LOG.info('exportService.loadExportData({}) ohne äußere Transaktion -> Gruppen {}, Carts {}',
                mitGroupId ? gid : null, result*.groupId(), rows)

        then:
        result*.groupId() == [gid]
        rows*.userName() == ['Anna']
        rows*.category() == [kategorieName]
        rows*.groupName() == [gruppenName]

        where:
        umfang          | mitGroupId
        'Einzelgruppe'  | true
        'Alle Gruppen'  | false
    }

    // Codex-Review 5 (optional): Der Endpoint ruft export() auf; dessen loadExportData()-Aufruf ist ein Selbstaufruf am
    // Proxy vorbei, die Transaktion kommt also nur aus der eigenen @Transactional von export().
    def "export schreibt die Datei der Einzelgruppe ohne äußere Transaktion (#format)"() {
        given: "ohne Request liest der Translator die JVM-Locale; Blattname „Ausgaben“ braucht de"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben])
        fixtures.cart(wg, title: 'Pizza', amount: 30d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        loginAs(ben)
        def gid = wg.group.id
        def response = new MockHttpServletResponse()
        LOG.info('Szenario: Gruppe {}, Owner Anna {}, Mitglied Ben {} (angemeldet), Cart Pizza (committet), Format {}',
                gid, anna.id, ben.id, format)

        when:
        exportService.export(format, gid, response)
        def titel = format == ExportFormat.XLSX
                ? TestData.workbook(response.contentAsByteArray).getSheet('Ausgaben').getRow(1).getCell(1).stringCellValue
                : TestData.unzip(response.contentAsByteArray).values().first().readLines()[1].split(';')[1]
        LOG.info('exportService.export({}, {}) ohne äußere Transaktion -> {} Bytes, Content-Type {}, Titel {}',
                format, gid, response.contentAsByteArray.length, response.contentType, titel)

        then:
        response.contentType == contentType
        titel == 'Pizza'

        cleanup:
        LocaleContextHolder.resetLocaleContext()

        where:
        format           | contentType
        ExportFormat.XLSX | 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        ExportFormat.CSV  | 'application/zip'
    }
}
