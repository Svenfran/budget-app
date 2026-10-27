package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingItemOrderDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingItemDoesNotBelongToShoppingListException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingItemNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingListDoesNotBelongToGroupException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingListNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Einträge der Einkaufslisten: Zustand vorher/nachher, Rechte (VerificationService) und Auslösung der
 * Benachrichtigungen (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft NotificationServiceSpec.
 *
 * lastUpdateShoppingList setzt der Service aus dem Clock (testClock, Standard REFERENCE_DATE). Die Gruppen starten
 * mit einem Wert einen Tag davor (START); geprüft wird „auf testClock gesetzt“ bzw. „unverändert“.
 */
class ShoppingItemServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingItemServiceIntegrationSpec)

    private static final Date START = DateUtils.toDate(REFERENCE_DATE.minusDays(1))

    /** ID, die es in keiner Tabelle gibt. */
    private static final Long UNBEKANNT = -1L

    @Autowired ShoppingItemService shoppingItemService
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired GroupRepository groupRepository

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

    def "Eintrag anlegen speichert ihn offen an der Liste und setzt lastUpdateShoppingList auf jetzt"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: heute {}, Gruppe {}, lastUpdateShoppingList {}, Mitglied {} (angemeldet), leere Liste {}; neuer Eintrag "Milch"',
                testClock.today, groupId, START, ben.id, listId)

        when:
        def result = shoppingItemService.addShoppingItem(
                new AddEditShoppingItemDto(name: 'Milch', shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def gespeichert = shoppingItemRepository.findById(result.id).get()
        def gespeichertListId = gespeichert.shoppingList.id
        def eintraegeDerListe = eintragIds(listId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.addShoppingItem(Liste {}) -> Eintrag {} "{}", completed {}, an Liste {}; Einträge der Liste {}, lastUpdateShoppingList {}',
                listId, result.id, gespeichert.name, gespeichert.completed, gespeichertListId, eintraegeDerListe, lastUpdate)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, _, 'add')
        gespeichert.name == 'Milch'
        !gespeichert.completed
        gespeichertListId == listId
        eintraegeDerListe == [result.id]
        result.groupId == groupId
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Eintrag ändern speichert Name und Erledigt-Status, die Liste bleibt"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste, name: 'Milch', completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}, Eintrag {} "Milch" offen -> "Hafermilch" erledigt',
                groupId, ben.id, listId, itemId)

        when:
        shoppingItemService.updateShoppingItem(new AddEditShoppingItemDto(
                id: itemId, name: 'Hafermilch', completed: true, shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def gespeichert = shoppingItemRepository.findById(itemId).get()
        def gespeichertListId = gespeichert.shoppingList.id
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.updateShoppingItem(Eintrag {}) -> "{}", completed {}, Liste {}, lastUpdateShoppingList {}',
                itemId, gespeichert.name, gespeichert.completed, gespeichertListId, lastUpdate)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, _, 'update')
        gespeichert.name == 'Hafermilch'
        gespeichert.completed
        gespeichertListId == listId
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Eintrag löschen entfernt nur diesen Eintrag"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste)
        def brot = fixtures.shoppingItem(liste)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def brotId = brot.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Einträgen {} (wird gelöscht) und {}',
                groupId, ben.id, listId, milchId, brotId)

        when:
        shoppingItemService.deleteShoppingItem(new AddEditShoppingItemDto(id: milchId, shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def eintraegeDerListe = eintragIds(listId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.deleteShoppingItem(Eintrag {}) -> Einträge der Liste {}, lastUpdateShoppingList {}',
                milchId, eintraegeDerListe, lastUpdate)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, _, 'delete')
        eintraegeDerListe == [brotId]
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Erledigte Einträge löschen entfernt nur die als erledigt gesendeten Einträge"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: true)
        def brot = fixtures.shoppingItem(liste, completed: false)
        def kaffee = fixtures.shoppingItem(liste, completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def brotId = brot.id
        def kaffeeId = kaffee.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}: gesendet {} erledigt und {} offen; {} nicht gesendet',
                groupId, ben.id, listId, milchId, brotId, kaffeeId)

        when:
        shoppingItemService.deleteAllCompletedShoppingItems([
                new AddEditShoppingItemDto(id: milchId, completed: true, shoppingListId: listId, groupId: groupId),
                new AddEditShoppingItemDto(id: brotId, completed: false, shoppingListId: listId, groupId: groupId)])
        flushAndClear()
        def eintraegeDerListe = eintragIds(listId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems(Liste {}, {} erledigt, {} offen) -> Einträge der Liste {}, lastUpdateShoppingList {}',
                listId, milchId, brotId, eintraegeDerListe, lastUpdate)

        then: 'die Benachrichtigung enthält nur den gelöschten Eintrag: Empfänger entfernen alle übermittelten IDs'
        1 * notificationService.sendShoppingItemDeleteAllNotification(groupId, { it*.id == [milchId] })
        eintraegeDerListe == [brotId, kaffeeId]
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Erledigte Einträge löschen mit nur offenen Einträgen löscht nichts und benachrichtigt mit leerer Liste"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: false)
        def brot = fixtures.shoppingItem(liste, completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def brotId = brot.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}: gesendet {} und {}, beide offen',
                groupId, ben.id, listId, milchId, brotId)

        when:
        shoppingItemService.deleteAllCompletedShoppingItems([milchId, brotId].collect {
            new AddEditShoppingItemDto(id: it, completed: false, shoppingListId: listId, groupId: groupId)
        })
        flushAndClear()
        def eintraegeDerListe = eintragIds(listId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems(Liste {}, {} und {} offen) -> Einträge der Liste {}, lastUpdateShoppingList {} (Ist-Verhalten: wird auf jetzt gesetzt)',
                listId, milchId, brotId, eintraegeDerListe, lastUpdate)

        then: 'NotificationService verwirft eine leere Liste (NotificationServiceSpec), Empfänger erhalten also nichts'
        1 * notificationService.sendShoppingItemDeleteAllNotification(groupId, { it.isEmpty() })
        0 * notificationService._
        eintraegeDerListe == [milchId, brotId]
    }

    def "Erledigte Einträge löschen mit leerer Liste ändert nichts und benachrichtigt niemanden"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit erledigtem Eintrag {}; gesendet wird eine leere Liste',
                groupId, ben.id, listId, milchId)

        when:
        shoppingItemService.deleteAllCompletedShoppingItems([])
        flushAndClear()
        def eintraegeDerListe = eintragIds(listId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems([]) -> Einträge der Liste {}, lastUpdateShoppingList {}',
                eintraegeDerListe, lastUpdate)

        then:
        0 * notificationService._
        eintraegeDerListe == [milchId]
        lastUpdate.time == START.time
    }

    def "Nicht-Mitglied wird bei #aktion abgewiesen, nichts ändert sich"() {
        given:
        def fremder = fixtures.user()
        def wg = fixtures.group()
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste, name: 'Alt', completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Owner {}, Liste {} mit Eintrag {} "Alt"; Nicht-Mitglied {} (angemeldet) versucht {}',
                groupId, wg.owner.id, listId, itemId, fremder.id, aktion)

        when:
        LOG.info('shoppingItemService {} (Gruppe {}, Liste {}, Eintrag {}), erwartet NotOwnerOrMemberOfGroupException',
                aktion, groupId, listId, itemId)
        ausfuehren(aktion, groupId, listId, itemId)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        0 * notificationService._
        flushAndClear()
        eintragIds(listId) == [itemId]
        def unveraendert = shoppingItemRepository.findById(itemId).get()
        unveraendert.name == 'Alt'
        !unveraendert.completed
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['add', 'update', 'delete', 'delete-all']
    }

    def "#aktion mit Liste einer fremden Gruppe wird abgewiesen, die fremde Liste bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def fremderEintrag = fixtures.shoppingItem(fremdeListe, name: 'Fremd', completed: false)
        def groupId = wg.group.id
        def andereGroupId = andereGruppe.group.id
        def fremdeListId = fremdeListe.id
        def fremderEintragId = fremderEintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet); Liste {} mit Eintrag {} "Fremd" gehört Gruppe {}',
                groupId, ben.id, fremdeListId, fremderEintragId, andereGroupId)

        when:
        LOG.info('shoppingItemService {} (Gruppe {}, Liste {}, Eintrag {}), erwartet ShoppingListDoesNotBelongToGroupException',
                aktion, groupId, fremdeListId, fremderEintragId)
        ausfuehren(aktion, groupId, fremdeListId, fremderEintragId)

        then:
        thrown(ShoppingListDoesNotBelongToGroupException)
        0 * notificationService._
        flushAndClear()
        eintragIds(fremdeListId) == [fremderEintragId]
        def unveraendert = shoppingItemRepository.findById(fremderEintragId).get()
        unveraendert.name == 'Fremd'
        !unveraendert.completed
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['add', 'update', 'delete', 'delete-all']
    }

    def "#aktion mit Eintrag einer anderen Liste der Gruppe wird abgewiesen, der Eintrag bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def andereListe = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(andereListe, name: 'Andere', completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def andereListId = andereListe.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Listen {} und {}; Eintrag {} "Andere" gehört Liste {}, gesendet mit Liste {}',
                groupId, ben.id, listId, andereListId, itemId, andereListId, listId)

        when:
        LOG.info('shoppingItemService {} (Liste {}, Eintrag {} an erster Stelle), erwartet ShoppingItemDoesNotBelongToShoppingListException',
                aktion, listId, itemId)
        ausfuehren(aktion, groupId, listId, itemId)

        then:
        thrown(ShoppingItemDoesNotBelongToShoppingListException)
        0 * notificationService._
        flushAndClear()
        eintragIds(andereListId) == [itemId]
        eintragIds(listId).isEmpty()
        def unveraendert = shoppingItemRepository.findById(itemId).get()
        unveraendert.name == 'Andere'
        !unveraendert.completed
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['update', 'delete', 'delete-all']
    }

    def "#aktion mit unbekanntem Eintrag: ShoppingItemNotFoundException"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste)
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Eintrag {}; Eintrag {} existiert nicht',
                groupId, ben.id, listId, itemId, UNBEKANNT)

        when:
        LOG.info('shoppingItemService {} (Liste {}, Eintrag {} an erster Stelle), erwartet ShoppingItemNotFoundException',
                aktion, listId, UNBEKANNT)
        ausfuehren(aktion, groupId, listId, UNBEKANNT)

        then:
        thrown(ShoppingItemNotFoundException)
        0 * notificationService._
        flushAndClear()
        eintragIds(listId) == [itemId]
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['update', 'delete-all']
    }

    def "Eintrag in einer unbekannten Liste anlegen: ShoppingListNotFoundException"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} existiert nicht', groupId, ben.id, UNBEKANNT)

        when:
        LOG.info('shoppingItemService.addShoppingItem(Liste {}), erwartet ShoppingListNotFoundException', UNBEKANNT)
        ausfuehren('add', groupId, UNBEKANNT, null)

        then:
        thrown(ShoppingListNotFoundException)
        0 * notificationService._
        flushAndClear()
        lastUpdateShoppingList(groupId).time == START.time
    }

    def "Erledigte Einträge aus zwei Listen löschen: IllegalArgumentException, nichts wird gelöscht"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def erste = fixtures.shoppingList(wg)
        def zweite = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(erste, completed: true)
        def brot = fixtures.shoppingItem(zweite, completed: true)
        def groupId = wg.group.id
        def ersteId = erste.id
        def zweiteId = zweite.id
        def milchId = milch.id
        def brotId = brot.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), erledigter Eintrag {} in Liste {}, erledigter Eintrag {} in Liste {}',
                groupId, ben.id, milchId, ersteId, brotId, zweiteId)

        when:
        // Ist-Verhalten (L3 im Plan DIV-16): nicht gemappt, über HTTP 500.
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems(Listen {} und {} gemischt), erwartet IllegalArgumentException',
                ersteId, zweiteId)
        shoppingItemService.deleteAllCompletedShoppingItems([
                new AddEditShoppingItemDto(id: milchId, completed: true, shoppingListId: ersteId, groupId: groupId),
                new AddEditShoppingItemDto(id: brotId, completed: true, shoppingListId: zweiteId, groupId: groupId)])

        then:
        thrown(IllegalArgumentException)
        0 * notificationService._
        flushAndClear()
        eintragIds(ersteId) == [milchId]
        eintragIds(zweiteId) == [brotId]
        lastUpdateShoppingList(groupId).time == START.time
    }

    def "Anlegen mit der ID eines fremden Eintrags legt einen neuen Eintrag an, der fremde bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def fremderEintrag = fixtures.shoppingItem(fremdeListe, name: 'Fremd', completed: false)
        def groupId = wg.group.id
        def listId = liste.id
        def fremdeListId = fremdeListe.id
        def fremderEintragId = fremderEintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}; Eintrag {} "Fremd" in Liste {} der Gruppe {}; Anlegen mit id {}',
                groupId, ben.id, listId, fremderEintragId, fremdeListId, andereGruppe.group.id, fremderEintragId)

        when:
        def result = shoppingItemService.addShoppingItem(new AddEditShoppingItemDto(
                id: fremderEintragId, name: 'Gekapert', completed: true, shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def eigeneEintraege = eintragIds(listId)
        def fremd = shoppingItemRepository.findById(fremderEintragId).get()
        def fremdListId = fremd.shoppingList.id
        LOG.info('shoppingItemService.addShoppingItem(id {}, Liste {}) -> Eintrag {}; Einträge der Liste {}; Eintrag {} "{}", completed {}, Liste {}',
                fremderEintragId, listId, result.id, eigeneEintraege, fremderEintragId, fremd.name, fremd.completed, fremdListId)

        then:
        1 * notificationService.sendShoppingItemNotification(groupId, _, 'add')
        result.id != fremderEintragId
        eigeneEintraege == [result.id]
        fremd.name == 'Fremd'
        !fremd.completed
        fremdListId == fremdeListId
    }

    def "Erledigte Einträge löschen mit #zweiter (#completedText) an zweiter Stelle: #erwartet.simpleName, nichts wird gelöscht"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: true)
        def andereListe = fixtures.shoppingList(wg)
        def andererEintrag = fixtures.shoppingItem(andereListe, completed: true)
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def fremderEintrag = fixtures.shoppingItem(fremdeListe, completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        def andererEintragId = andererEintrag.id
        def fremderEintragId = fremderEintrag.id
        def zweiteId = ['Eintrag einer anderen Liste'    : andererEintragId,
                        'Eintrag einer fremden Gruppe'   : fremderEintragId,
                        'unbekannte ID'                  : UNBEKANNT][zweiter]
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit erledigtem Eintrag {}; Eintrag {} in anderer Liste {}, Eintrag {} in Gruppe {}; gesendet: {}, dann {} (completed {})',
                groupId, ben.id, listId, milchId, andererEintragId, andereListe.id, fremderEintragId, andereGruppe.group.id,
                milchId, zweiteId, completed)

        when:
        LOG.info('shoppingItemService.deleteAllCompletedShoppingItems(Liste {}, Einträge {}, {}), erwartet {}',
                listId, milchId, zweiteId, erwartet.simpleName)
        shoppingItemService.deleteAllCompletedShoppingItems([
                new AddEditShoppingItemDto(id: milchId, completed: true, shoppingListId: listId, groupId: groupId),
                new AddEditShoppingItemDto(id: zweiteId, completed: completed, shoppingListId: listId, groupId: groupId)])

        then:
        thrown(erwartet)
        0 * notificationService._
        flushAndClear()
        eintragIds(listId) == [milchId]
        shoppingItemRepository.existsById(andererEintragId)
        shoppingItemRepository.existsById(fremderEintragId)
        lastUpdateShoppingList(groupId).time == START.time

        where:
        zweiter                        | completed || erwartet
        'Eintrag einer anderen Liste'  | true      || ShoppingItemDoesNotBelongToShoppingListException
        'Eintrag einer fremden Gruppe' | true      || ShoppingItemDoesNotBelongToShoppingListException
        'Eintrag einer fremden Gruppe' | false     || ShoppingItemDoesNotBelongToShoppingListException
        'unbekannte ID'                | true      || ShoppingItemNotFoundException
        'unbekannte ID'                | false     || ShoppingItemNotFoundException

        completedText = completed ? 'erledigt' : 'offen'
    }

    private void ausfuehren(String aktion, Long groupId, Long listId, Long itemId) {
        switch (aktion) {
            case 'add':
                shoppingItemService.addShoppingItem(new AddEditShoppingItemDto(name: 'Neu', shoppingListId: listId, groupId: groupId))
                break
            case 'update':
                shoppingItemService.updateShoppingItem(new AddEditShoppingItemDto(
                        id: itemId, name: 'Neu', completed: true, shoppingListId: listId, groupId: groupId))
                break
            case 'delete':
                shoppingItemService.deleteShoppingItem(new AddEditShoppingItemDto(id: itemId, shoppingListId: listId, groupId: groupId))
                break
            case 'delete-all':
                shoppingItemService.deleteAllCompletedShoppingItems([
                        new AddEditShoppingItemDto(id: itemId, completed: true, shoppingListId: listId, groupId: groupId)])
                break
            default:
                throw new IllegalArgumentException("Unbekannte Aktion $aktion")
        }
    }

    // Reihenfolge (DIV-1): Position bei Anlage, Schutz vor Merge-Überschreibung

    def "Neuer Eintrag kommt ans Ende: Position max + 1 auch bei Lücken, Einträge anderer Listen zählen nicht"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        fixtures.shoppingItem(liste, sortOrder: 0)
        fixtures.shoppingItem(liste, sortOrder: 4)
        def andereListe = fixtures.shoppingList(wg)
        fixtures.shoppingItem(andereListe, sortOrder: 8)
        def groupId = wg.group.id
        def listId = liste.id
        def andereListId = andereListe.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: heute {}, Gruppe {} (lastUpdateShoppingList {}), Mitglied {} (angemeldet), Liste {} mit Einträgen an Position 0 und 4; Liste {} mit Eintrag an Position 8',
                testClock.today, groupId, START, ben.id, listId, andereListId)

        when:
        def result = shoppingItemService.addShoppingItem(
                new AddEditShoppingItemDto(name: 'Milch', shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def position = shoppingItemRepository.findById(result.id).get().sortOrder
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingItemService.addShoppingItem(Liste {}) -> Eintrag {} an Position {}, lastUpdateShoppingList {}',
                listId, result.id, position, lastUpdate)

        then:
        position == 5
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Eintrag #aktion lässt seine Position unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste, name: 'Milch', completed: false, sortOrder: 3)
        def groupId = wg.group.id
        def listId = liste.id
        def itemId = eintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {}, Eintrag {} "Milch" offen an Position 3 -> "{}", erledigt {}',
                groupId, ben.id, listId, itemId, name, completed)

        when:
        shoppingItemService.updateShoppingItem(new AddEditShoppingItemDto(
                id: itemId, name: name, completed: completed, shoppingListId: listId, groupId: groupId))
        flushAndClear()
        def gespeichert = shoppingItemRepository.findById(itemId).get()
        def (gespeichertName, gespeichertCompleted, position) = [gespeichert.name, gespeichert.completed, gespeichert.sortOrder]
        LOG.info('shoppingItemService.updateShoppingItem(Eintrag {}) -> "{}", erledigt {}, Position {}',
                itemId, gespeichertName, gespeichertCompleted, position)

        then:
        gespeichertName == name
        gespeichertCompleted == completed
        position == 3

        where:
        aktion       | name         | completed
        'abhaken'    | 'Milch'      | true
        'umbenennen' | 'Hafermilch' | false
    }

    def "Einträge umsortieren speichert die Reihenfolge, erhöht die Revision der Liste und meldet sie mit reorder-items"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg, itemOrderRevision: 4L)
        def x = fixtures.shoppingItem(liste, sortOrder: 0)
        def y = fixtures.shoppingItem(liste, sortOrder: 1)
        def z = fixtures.shoppingItem(liste, sortOrder: 2)
        def groupId = wg.group.id
        def listId = liste.id
        def (xId, yId, zId) = [x.id, y.id, z.id]
        def gesendet = null
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {} (lastUpdateShoppingList {}), Mitglied {} (angemeldet), Liste {} (itemOrderRevision 4) mit Einträgen {} -> gewünscht {}',
                groupId, START, ben.id, listId, [xId, yId, zId], [zId, xId, yId])

        when:
        def result = shoppingItemService.reorderShoppingItems(
                new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [zId, xId, yId]))
        flushAndClear()
        def positionen = [xId, yId, zId].collectEntries { [it, shoppingItemRepository.findById(it).get().sortOrder] }
        def revision = shoppingListRepository.findById(listId).get().itemOrderRevision
        def gruppe = groupRepository.findById(groupId).get()
        LOG.info('shoppingItemService.reorderShoppingItems(Liste {}) -> revision {}, IDs {}; Positionen {}, itemOrderRevision {}, listOrderRevision {}, lastUpdateShoppingList {}',
                listId, result.revision, result.shoppingItemIds, positionen, revision, gruppe.listOrderRevision, gruppe.lastUpdateShoppingList)

        then:
        1 * notificationService.sendShoppingItemOrderNotification(groupId, _) >> { gesendet = it[1] }
        0 * notificationService._
        result.groupId == groupId
        result.shoppingListId == listId
        result.revision == 5L
        result.shoppingItemIds == [zId, xId, yId]
        gesendet.is(result)
        positionen == [(zId): 0, (xId): 1, (yId): 2]
        revision == 5L
        gruppe.listOrderRevision == 0L
        gruppe.lastUpdateShoppingList.time == testClock.instant().toEpochMilli()
    }

    def "Einträge umsortieren übernimmt tolerant: Einträge anderer Listen, unbekannte und doppelte IDs ignoriert, fehlende hinten"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def x = fixtures.shoppingItem(liste, sortOrder: 0)
        def y = fixtures.shoppingItem(liste, sortOrder: 1)
        def z = fixtures.shoppingItem(liste, sortOrder: 2)
        def andereListe = fixtures.shoppingList(wg)
        def w = fixtures.shoppingItem(andereListe, sortOrder: 7)
        def groupId = wg.group.id
        def listId = liste.id
        def andereListId = andereListe.id
        def (xId, yId, zId, wId) = [x.id, y.id, z.id, w.id]
        def gewuenscht = [wId, zId, UNBEKANNT, zId, xId]
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Einträgen {}; Liste {} mit Eintrag {} (Position 7); gewünscht {}',
                groupId, ben.id, listId, [xId, yId, zId], andereListId, wId, gewuenscht)

        when:
        def result = shoppingItemService.reorderShoppingItems(
                new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: gewuenscht))
        flushAndClear()
        def wPosition = shoppingItemRepository.findById(wId).get().sortOrder
        def andereRevision = shoppingListRepository.findById(andereListId).get().itemOrderRevision
        LOG.info('shoppingItemService.reorderShoppingItems(Liste {}) -> IDs {}; Eintrag {} Position {}, Revision der Liste {}: {}',
                listId, result.shoppingItemIds, wId, wPosition, andereListId, andereRevision)

        then:
        1 * notificationService.sendShoppingItemOrderNotification(groupId, _)
        result.shoppingItemIds == [zId, xId, yId]
        wPosition == 7
        andereRevision == 0L
    }

    def "Einträge umsortieren mit #fall wird abgewiesen, Reihenfolge und Revision bleiben"() {
        given:
        def ben = fixtures.user()
        def fremder = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def andereGruppe = fixtures.group()
        def liste = fallFremdeListe ? fixtures.shoppingList(andereGruppe) : fixtures.shoppingList(wg)
        def x = fixtures.shoppingItem(liste, sortOrder: 0)
        def y = fixtures.shoppingItem(liste, sortOrder: 1)
        def groupId = wg.group.id
        def listId = liste.id
        def (xId, yId) = [x.id, y.id]
        def angemeldet = fallFremdeListe ? ben : fremder
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {} (Mitglied {}), Liste {} mit Einträgen {} in Gruppe {}; angemeldet {} ({})',
                groupId, ben.id, listId, [xId, yId], fallFremdeListe ? andereGruppe.group.id : groupId, angemeldet.id, fall)

        when:
        LOG.info('shoppingItemService.reorderShoppingItems(Gruppe {}, Liste {}), erwartet {}', groupId, listId, erwartet.simpleName)
        shoppingItemService.reorderShoppingItems(
                new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [yId, xId]))

        then:
        thrown(erwartet)
        0 * notificationService._
        flushAndClear()
        shoppingListRepository.findById(listId).get().itemOrderRevision == 0L
        [xId, yId].collect { shoppingItemRepository.findById(it).get().sortOrder } == [0, 1]
        lastUpdateShoppingList(groupId).time == START.time

        where:
        fall                          | fallFremdeListe || erwartet
        'Liste einer fremden Gruppe'  | true            || ShoppingListDoesNotBelongToGroupException
        'Nicht-Mitglied'              | false           || NotOwnerOrMemberOfGroupException
    }

    /** IDs der Einträge einer Liste, aufsteigend (lädt die Liste neu; vorher flushAndClear()). */
    private List<Long> eintragIds(Long listId) {
        shoppingListRepository.findById(listId).get().shoppingItems*.id.sort()
    }

    private Date lastUpdateShoppingList(Long groupId) {
        groupRepository.findById(groupId).get().lastUpdateShoppingList
    }
}
