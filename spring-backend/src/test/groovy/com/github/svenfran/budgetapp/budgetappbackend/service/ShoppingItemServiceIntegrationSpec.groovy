package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Auslösung der Eintrags-Benachrichtigungen (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft
 * NotificationServiceSpec. Ausgelöst wird jeweils durch ein Mitglied (nicht den Owner).
 */
class ShoppingItemServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingItemServiceIntegrationSpec)

    @Autowired ShoppingItemService shoppingItemService

    def "Eintrag anlegen löst add-item mit dem gespeicherten Eintrag aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        def gesendet = null
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}; neuer Eintrag "Milch"', groupId, ben.id, listId)

        when:
        def result = shoppingItemService.addShoppingItem(
                new AddEditShoppingItemDto(name: 'Milch', shoppingListId: listId, groupId: groupId))
        LOG.info('shoppingItemService.addShoppingItem(Liste {}, "Milch") -> Eintrag {}', listId, result.id)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, _, 'add') >> { gesendet = it[1] }
        0 * notificationService._
        gesendet.id == result.id
        gesendet.name == 'Milch'
        gesendet.shoppingListId == listId
    }

    def "Eintrag ändern löst update-item mit den geänderten Werten aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste, name: 'Milch')
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Eintrag {} "Milch" -> "Hafermilch", erledigt', groupId, ben.id, itemId)

        when:
        def result = shoppingItemService.updateShoppingItem(new AddEditShoppingItemDto(
                id: itemId, name: 'Hafermilch', completed: true, shoppingListId: listId, groupId: groupId))
        LOG.info('shoppingItemService.updateShoppingItem(Eintrag {}) -> name "{}", completed {}', itemId, result.name, result.completed)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId,
                { it.id == itemId && it.name == 'Hafermilch' && it.completed }, 'update')
        0 * notificationService._
    }

    def "Eintrag löschen löst delete-item für den gelöschten Eintrag aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste)
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}, Eintrag {}', groupId, ben.id, listId, itemId)

        when:
        shoppingItemService.deleteShoppingItem(new AddEditShoppingItemDto(id: itemId, shoppingListId: listId, groupId: groupId))
        LOG.info('shoppingItemService.deleteShoppingItem(Eintrag {}) aufgerufen', itemId)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, { it.id == itemId }, 'delete')
        0 * notificationService._
    }

    def "Erledigte Einträge löschen löst delete-all-items mit beiden Einträgen in Reihenfolge aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, name: 'Milch', completed: true)
        def brot = fixtures.shoppingItem(liste, name: 'Brot', completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def brotId = brot.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}, erledigte Einträge {} und {}',
                groupId, ben.id, listId, milchId, brotId)

        when:
        shoppingItemService.deleteAllCompletedShoppingItems([milchId, brotId].collect {
            new AddEditShoppingItemDto(id: it, completed: true, shoppingListId: listId, groupId: groupId)
        })
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems(Liste {}, Einträge {}, {}) aufgerufen', listId, milchId, brotId)

        then:
        1 * notificationService.sendShoppingItemDeleteAllNotification(groupId, { it*.id == [milchId, brotId] })
        0 * notificationService._
    }
}
