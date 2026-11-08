package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
}
