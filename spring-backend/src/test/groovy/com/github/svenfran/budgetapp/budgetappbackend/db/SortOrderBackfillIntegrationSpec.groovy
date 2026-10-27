package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Daten-SQL der DIV-1-Migration (Position der Einkaufslisten je Gruppe und der Einträge je Liste aus der bisherigen
 * Anzeigereihenfolge nach id). Die Testdatenbank hat das Changeset beim Start auf leeren Tabellen ausgeführt; hier wird
 * der Zustand davor nachgebaut (alle Positionen mit einem Platzhalter) und dieselbe SQL-Datei erneut ausgeführt. Die
 * Anweisungen ändern alle Zeilen, der Rollback der Test-Transaktion hält das folgenlos; geprüft werden nur die eigenen
 * Listen und Einträge.
 */
class SortOrderBackfillIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SortOrderBackfillIntegrationSpec)

    private static final String BACKFILL_SQL = '/db/changelog/release-1.x/sql/20261026120000-1_backfillSortOrder.sql'

    private static final int PLATZHALTER = 99

    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository

    def "Backfill nummeriert Listen je Gruppe und Einträge je Liste ab 0 in id-Reihenfolge"() {
        given:
        def wg = fixtures.group()
        def andereWg = fixtures.group()
        def listen = (1..3).collect { fixtures.shoppingList(wg, sortOrder: PLATZHALTER) }
        def andereListe = fixtures.shoppingList(andereWg, sortOrder: PLATZHALTER)
        def eintraege = (1..3).collect { fixtures.shoppingItem(listen[0], sortOrder: PLATZHALTER) }
        def andererEintrag = fixtures.shoppingItem(listen[1], sortOrder: PLATZHALTER)
        def listIds = listen*.id
        def andereListId = andereListe.id
        def itemIds = eintraege*.id
        def andererEintragId = andererEintrag.id
        flushAndClear()
        LOG.info('Szenario: Gruppe {} mit Listen {}, Gruppe {} mit Liste {}; Liste {} mit Einträgen {}, Liste {} mit Eintrag {}; alle Positionen {}',
                wg.group.id, listIds, andereWg.group.id, andereListId, listIds[0], itemIds, listIds[1], andererEintragId, PLATZHALTER)

        when:
        runBackfill()
        flushAndClear()
        def listenPositionen = listIds.collect { shoppingListRepository.findById(it).get().sortOrder }
        def andereListePosition = shoppingListRepository.findById(andereListId).get().sortOrder
        def eintragPositionen = itemIds.collect { shoppingItemRepository.findById(it).get().sortOrder }
        def andererEintragPosition = shoppingItemRepository.findById(andererEintragId).get().sortOrder
        LOG.info('Backfill -> Positionen Listen {}, Liste der anderen Gruppe {}, Einträge {}, Eintrag der zweiten Liste {}',
                listenPositionen, andereListePosition, eintragPositionen, andererEintragPosition)

        then:
        listenPositionen == [0, 1, 2]
        andereListePosition == 0
        eintragPositionen == [0, 1, 2]
        andererEintragPosition == 0
    }

    private void runBackfill() {
        def sql = getClass().getResource(BACKFILL_SQL).getText('UTF-8')
        sql.split(';')*.trim().findAll { it }.each {
            entityManager.createNativeQuery(it).executeUpdate()
        }
    }
}
