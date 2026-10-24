package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
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
 * lastUpdateShoppingList setzt der Service mit new Date() (nicht über den Clock). Die Gruppen starten deshalb mit
 * einem festen alten Wert (START); geprüft wird „erhöht“ bzw. „unverändert“.
 */
class ShoppingItemServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingItemServiceIntegrationSpec)

    private static final Date START = DateUtils.toDate(REFERENCE_DATE)

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

    def "Eintrag anlegen speichert ihn offen an der Liste und erhöht lastUpdateShoppingList"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, lastUpdateShoppingList {}, Mitglied {} (angemeldet), leere Liste {}; neuer Eintrag "Milch"',
                groupId, START, ben.id, listId)

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
        lastUpdate.time > START.time
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
        lastUpdate.time > START.time
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
        lastUpdate.time > START.time
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

        then:
        // Payload hier bewusst nicht geprüft: er enthält auch das nicht gelöschte DTO (Befund B1 im Plan DIV-16).
        1 * notificationService.sendShoppingItemDeleteAllNotification(groupId, _)
        eintraegeDerListe == [brotId, kaffeeId]
        lastUpdate.time > START.time
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

    /** IDs der Einträge einer Liste, aufsteigend (lädt die Liste neu; vorher flushAndClear()). */
    private List<Long> eintragIds(Long listId) {
        shoppingListRepository.findById(listId).get().shoppingItems*.id.sort()
    }

    private Date lastUpdateShoppingList(Long groupId) {
        groupRepository.findById(groupId).get().lastUpdateShoppingList
    }
}
