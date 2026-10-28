package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingPurchaseRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * Einkaufshistorie (DIV-23) über Transaktionsgrenzen: ein Kauf wird höchstens einmal erfasst, und er wird nur
 * zusammen mit dem Löschen des Eintrags committet.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; Transaktionen über TransactionTemplate bzw. die
 * eigene @Transactional der Services.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShoppingPurchaseServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingPurchaseServiceIntegrationSpec)

    @Autowired ShoppingPurchaseService shoppingPurchaseService
    @Autowired ShoppingItemService shoppingItemService
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired ShoppingPurchaseRepository shoppingPurchaseRepository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Kauf erfassen in zwei Transaktionen mit demselben Eintrag erfasst ihn einmal, offene Einträge nicht"() {
        given:
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, name: 'Milch', completed: true)
        def brot = fixtures.shoppingItem(liste, name: 'Brot', completed: false)
        def listId = liste.id
        def milchId = milch.id
        LOG.info('Szenario: Gruppe {}, Liste {} mit Milch {} erledigt und Brot {} offen', wg.group.id, listId, milchId, brot.id)

        when:
        2.times { transaction.executeWithoutResult { shoppingPurchaseService.recordPurchases(liste, [milch, brot]) } }
        def kaufIds = kaufIds(listId)
        LOG.info('shoppingPurchaseService.recordPurchases(Liste {}, Milch, Brot) zweimal committet -> Käufe {}', listId, kaufIds)

        then:
        kaufIds == [milchId]
    }

    def "Fehler nach dem Erfassen beim #aktion: der Eintrag bleibt, kein Kauf wird committet"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, name: 'Milch', completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def dto = new AddEditShoppingItemDto(id: milchId, completed: true, shoppingListId: listId, groupId: groupId)
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit erledigtem Eintrag {}; die Benachrichtigung nach dem Löschen scheitert',
                groupId, ben.id, listId, milchId)

        when:
        LOG.info('shoppingItemService {} (Eintrag {}), erwartet IllegalStateException aus der Benachrichtigung', aktion, milchId)
        aktion == 'Einzel-Löschen'
                ? shoppingItemService.deleteShoppingItem(dto)
                : shoppingItemService.deleteAllCompletedShoppingItems([dto])

        then:
        notificationService.sendShoppingItemNotification(*_) >> { throw new IllegalStateException('Testfehler') }
        notificationService.sendShoppingItemDeleteAllNotification(*_) >> { throw new IllegalStateException('Testfehler') }
        thrown(IllegalStateException)
        shoppingItemRepository.existsById(milchId)
        kaufIds(listId).isEmpty()

        where:
        aktion << ['Einzel-Löschen', 'Erledigte löschen']
    }

    private List<Long> kaufIds(Long listId) {
        shoppingPurchaseRepository.findByShoppingListIdOrderByPurchasedAtDescIdDesc(listId)*.sourceItemId.sort()
    }
}
