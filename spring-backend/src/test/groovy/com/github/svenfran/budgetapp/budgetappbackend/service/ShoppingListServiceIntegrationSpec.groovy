package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Auslösung der Einkaufslisten-Benachrichtigungen (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft
 * NotificationServiceSpec. Ausgelöst wird jeweils durch ein Mitglied (nicht den Owner).
 */
class ShoppingListServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingListServiceIntegrationSpec)

    @Autowired ShoppingListService shoppingListService

    def "Liste anlegen löst add-list mit der gespeicherten Liste aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def gesendet = null
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {} (angemeldet) legt Liste "Wocheneinkauf" an', groupId, wg.owner.id, ben.id)

        when:
        def result = shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Wocheneinkauf', groupId: groupId))
        LOG.info('shoppingListService.addShoppingList(Gruppe {}, "Wocheneinkauf") -> Liste {}', groupId, result.id)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'add') >> { gesendet = it[1] }
        0 * notificationService._
        gesendet.id == result.id
        gesendet.name == 'Wocheneinkauf'
    }

    def "Liste umbenennen löst update-list mit dem neuen Namen aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg, name: 'Alt')
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} "Alt" -> "Neu"', groupId, ben.id, listId)

        when:
        def result = shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: listId, name: 'Neu', groupId: groupId))
        LOG.info('shoppingListService.updateShoppingList(Liste {}, "Neu") -> name "{}"', listId, result.name)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, { it.id == listId && it.name == 'Neu' }, 'update')
        0 * notificationService._
    }

    def "Liste löschen löst delete-list für die gelöschte Liste aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        fixtures.shoppingItem(liste)
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit einem Eintrag', groupId, ben.id, listId)

        when:
        shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: listId, groupId: groupId))
        LOG.info('shoppingListService.deleteShoppingList(Liste {}) aufgerufen', listId)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, { it.id == listId }, 'delete')
        0 * notificationService._
    }
}
