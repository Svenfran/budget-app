package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
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

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Einkaufslisten: Zustand vorher/nachher, Rechte (VerificationService) und Auslösung der Benachrichtigungen
 * (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft NotificationServiceSpec.
 *
 * lastUpdateShoppingList setzt der Service aus dem Clock (testClock, Standard REFERENCE_DATE). Die Gruppen starten
 * mit einem Wert einen Tag davor (START); geprüft wird „auf testClock gesetzt“ bzw. „unverändert“.
 */
class ShoppingListServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingListServiceIntegrationSpec)

    private static final Date START = DateUtils.toDate(REFERENCE_DATE.minusDays(1))

    /** ID, die es in keiner Tabelle gibt. */
    private static final Long UNBEKANNT = -1L

    @Autowired ShoppingListService shoppingListService
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired GroupRepository groupRepository

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

    def "Liste anlegen speichert sie in der Gruppe und setzt lastUpdateShoppingList auf jetzt"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: heute {}, Gruppe {} ohne Listen, lastUpdateShoppingList {}, Mitglied {} (angemeldet) legt "Wocheneinkauf" an',
                testClock.today, groupId, START, ben.id)

        when:
        def result = shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Wocheneinkauf', groupId: groupId))
        flushAndClear()
        def listen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingListService.addShoppingList(Gruppe {}) -> Liste {}; Listen der Gruppe {}, lastUpdateShoppingList {}',
                groupId, result.id, listen*.id, lastUpdate)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'add')
        listen*.id == [result.id]
        listen*.name == ['Wocheneinkauf']
        result.groupId == groupId
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Owner darf eine Liste anlegen"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} ohne Mitglieder, Owner {} (angemeldet) legt "Baumarkt" an', groupId, wg.owner.id)

        when:
        def result = shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Baumarkt', groupId: groupId))
        flushAndClear()
        def listen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
        LOG.info('shoppingListService.addShoppingList(Gruppe {}) -> Liste {}; Listen der Gruppe {}', groupId, result.id, listen*.id)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'add')
        listen*.name == ['Baumarkt']
    }

    def "Liste umbenennen ändert den Namen, die Einträge bleiben an der Liste"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg, name: 'Alt')
        def milch = fixtures.shoppingItem(liste, name: 'Milch')
        def groupId = wg.group.id
        def listId = liste.id
        def milchId = milch.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} "Alt" mit Eintrag {} -> "Neu"', groupId, ben.id, listId, milchId)

        when:
        shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: listId, name: 'Neu', groupId: groupId))
        flushAndClear()
        def gespeichert = shoppingListRepository.findById(listId).get()
        def gespeichertName = gespeichert.name
        def gespeichertGroupId = gespeichert.group.id
        def milchListId = shoppingItemRepository.findById(milchId).get().shoppingList.id
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingListService.updateShoppingList(Liste {}) -> name "{}", Gruppe {}, Eintrag {} an Liste {}, lastUpdateShoppingList {}',
                listId, gespeichertName, gespeichertGroupId, milchId, milchListId, lastUpdate)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'update')
        gespeichertName == 'Neu'
        gespeichertGroupId == groupId
        milchListId == listId
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Liste löschen entfernt sie samt Einträgen, andere Listen der Gruppe bleiben"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg, name: 'Weg')
        def milch = fixtures.shoppingItem(liste)
        def brot = fixtures.shoppingItem(liste)
        def andere = fixtures.shoppingList(wg, name: 'Bleibt')
        def kaffee = fixtures.shoppingItem(andere)
        def groupId = wg.group.id
        def listId = liste.id
        def andereId = andere.id
        def itemIds = [milch.id, brot.id]
        def kaffeeId = kaffee.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Einträgen {} wird gelöscht; Liste {} mit Eintrag {} bleibt',
                groupId, ben.id, listId, itemIds, andereId, kaffeeId)

        when:
        shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: listId, groupId: groupId))
        flushAndClear()
        def listen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
        def verbliebeneEintraege = (itemIds + kaffeeId).findAll { shoppingItemRepository.existsById(it) }
        def lastUpdate = lastUpdateShoppingList(groupId)
        LOG.info('shoppingListService.deleteShoppingList(Liste {}) -> Listen der Gruppe {}, verbliebene Einträge {}, lastUpdateShoppingList {}',
                listId, listen*.id, verbliebeneEintraege, lastUpdate)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'delete')
        listen*.id == [andereId]
        verbliebeneEintraege == [kaffeeId]
        lastUpdate.time == testClock.instant().toEpochMilli()
    }

    def "Nicht-Mitglied wird bei #aktion abgewiesen, nichts ändert sich"() {
        given:
        def fremder = fixtures.user()
        def wg = fixtures.group()
        wg.group.lastUpdateShoppingList = START
        def liste = fixtures.shoppingList(wg, name: 'Alt')
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Owner {}, Liste {} "Alt"; Nicht-Mitglied {} (angemeldet) versucht {}',
                groupId, wg.owner.id, listId, fremder.id, aktion)

        when:
        LOG.info('shoppingListService {} (Gruppe {}, Liste {}), erwartet NotOwnerOrMemberOfGroupException', aktion, groupId, listId)
        ausfuehren(aktion, groupId, listId)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        0 * notificationService._
        flushAndClear()
        def listen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
        listen*.id == [listId]
        listen*.name == ['Alt']
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['add', 'update', 'delete']
    }

    def "Entferntes Mitglied darf keine Liste mehr anlegen"() {
        given:
        def ehemalig = fixtures.user()
        def wg = fixtures.group()
        wg.group.lastUpdateShoppingList = START
        fixtures.membership(wg, ehemalig, membershipEnd: DateUtils.toDate(REFERENCE_DATE.minusMonths(1)))
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ehemalig)
        LOG.info('Szenario: Gruppe {}, Owner {}; Nutzer {} (angemeldet) war bis {} Mitglied, steht nicht mehr in members',
                groupId, wg.owner.id, ehemalig.id, REFERENCE_DATE.minusMonths(1))

        when:
        LOG.info('shoppingListService.addShoppingList(Gruppe {}), erwartet NotOwnerOrMemberOfGroupException', groupId)
        shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Neu', groupId: groupId))

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        0 * notificationService._
        flushAndClear()
        shoppingListRepository.findAllByGroup_IdOrderById(groupId).isEmpty()
        lastUpdateShoppingList(groupId).time == START.time
    }

    def "#aktion mit Liste einer fremden Gruppe wird abgewiesen, die fremde Liste bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe, name: 'Fremd')
        def fremderEintrag = fixtures.shoppingItem(fremdeListe)
        def groupId = wg.group.id
        def andereGroupId = andereGruppe.group.id
        def fremdeListId = fremdeListe.id
        def fremderEintragId = fremderEintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet); Liste {} "Fremd" mit Eintrag {} gehört Gruppe {}',
                groupId, ben.id, fremdeListId, fremderEintragId, andereGroupId)

        when:
        LOG.info('shoppingListService {} (Gruppe {}, Liste {}), erwartet ShoppingListDoesNotBelongToGroupException',
                aktion, groupId, fremdeListId)
        ausfuehren(aktion, groupId, fremdeListId)

        then:
        thrown(ShoppingListDoesNotBelongToGroupException)
        0 * notificationService._
        flushAndClear()
        def fremd = shoppingListRepository.findById(fremdeListId).get()
        fremd.name == 'Fremd'
        fremd.group.id == andereGroupId
        shoppingItemRepository.existsById(fremderEintragId)
        lastUpdateShoppingList(groupId).time == START.time

        where:
        aktion << ['update', 'delete']
    }

    def "Umbenennen einer unbekannten Liste: ShoppingListNotFoundException"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        wg.group.lastUpdateShoppingList = START
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} existiert nicht', groupId, ben.id, UNBEKANNT)

        when:
        LOG.info('shoppingListService.updateShoppingList(Liste {}), erwartet ShoppingListNotFoundException', UNBEKANNT)
        shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: UNBEKANNT, name: 'Neu', groupId: groupId))

        then:
        thrown(ShoppingListNotFoundException)
        0 * notificationService._
        flushAndClear()
        shoppingListRepository.findAllByGroup_IdOrderById(groupId).isEmpty()
        lastUpdateShoppingList(groupId).time == START.time
    }

    def "Anlegen in einer unbekannten Gruppe: GroupNotFoundException"() {
        given:
        def ben = fixtures.user()
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Nutzer {} (angemeldet), Gruppe {} existiert nicht', ben.id, UNBEKANNT)

        when:
        LOG.info('shoppingListService.addShoppingList(Gruppe {}), erwartet GroupNotFoundException', UNBEKANNT)
        shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Neu', groupId: UNBEKANNT))

        then:
        thrown(GroupNotFoundException)
        0 * notificationService._
    }

    def "Listen laden (#zeitstempel) liefert nur die eigenen Listen nach id, Einträge je Liste nach id"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def erste = fixtures.shoppingList(wg, name: 'Erste')
        def milch = fixtures.shoppingItem(erste, name: 'Milch')
        def brot = fixtures.shoppingItem(erste, name: 'Brot', completed: true)
        def zweite = fixtures.shoppingList(wg, name: 'Zweite')
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def groupId = wg.group.id
        def ids = [erste: erste.id, zweite: zweite.id, milch: milch.id, brot: brot.id, fremd: fremdeListe.id]
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Listen {} (Einträge {} offen, {} erledigt) und {}; fremde Liste {} in Gruppe {}',
                groupId, ben.id, ids.erste, ids.milch, ids.brot, ids.zweite, ids.fremd, andereGruppe.group.id)

        when:
        def result = shoppingListService.getShoppingListsForGroup(groupId, requestTimeStamp)
        def listen = result.get()
        LOG.info('shoppingListService.getShoppingListsForGroup(Gruppe {}, {}) -> Listen {}, Einträge der ersten {}',
                groupId, requestTimeStamp, listen*.id, listen[0].shoppingItems*.id)

        then:
        0 * notificationService._
        listen*.id == [ids.erste, ids.zweite]
        listen*.name == ['Erste', 'Zweite']
        listen[0].shoppingItems*.id == [ids.milch, ids.brot]
        listen[0].shoppingItems*.name == ['Milch', 'Brot']
        listen[0].shoppingItems*.completed == [false, true]
        listen[1].shoppingItems.isEmpty()

        where:
        zeitstempel                         | requestTimeStamp
        'ohne Zeitstempel'                  | null
        'Zeitstempel 1900-01-01 (Frontend)' | DateUtils.toDate(LocalDate.of(1900, 1, 1)).time
    }

    def "Listen laden für eine Gruppe ohne Listen liefert eine leere Liste"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {} ohne Einkaufslisten, Mitglied {} (angemeldet)', groupId, ben.id)

        when:
        def result = shoppingListService.getShoppingListsForGroup(groupId, null)
        LOG.info('shoppingListService.getShoppingListsForGroup(Gruppe {}, null) -> {}', groupId, result.get()*.id)

        then:
        result.get().isEmpty()
    }

    def "Nicht-Mitglied darf die Listen einer Gruppe nicht laden"() {
        given:
        def fremder = fixtures.user()
        def wg = fixtures.group()
        fixtures.shoppingList(wg)
        def groupId = wg.group.id
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {} mit einer Liste, Owner {}; Nicht-Mitglied {} (angemeldet)', groupId, wg.owner.id, fremder.id)

        when:
        LOG.info('shoppingListService.getShoppingListsForGroup(Gruppe {}, null), erwartet NotOwnerOrMemberOfGroupException', groupId)
        shoppingListService.getShoppingListsForGroup(groupId, null)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
    }

    def "Anlegen mit der ID einer fremden Liste legt eine neue Liste an, die fremde bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereGruppe = fixtures.group()
        andereGruppe.group.lastUpdateShoppingList = START
        def fremdeListe = fixtures.shoppingList(andereGruppe, name: 'Fremd')
        def fremderEintrag = fixtures.shoppingItem(fremdeListe)
        def groupId = wg.group.id
        def andereGroupId = andereGruppe.group.id
        def fremdeListId = fremdeListe.id
        def fremderEintragId = fremderEintrag.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet); Liste {} "Fremd" mit Eintrag {} gehört Gruppe {}; Anlegen mit id {}',
                groupId, ben.id, fremdeListId, fremderEintragId, andereGroupId, fremdeListId)

        when:
        def result = shoppingListService.addShoppingList(new AddEditShoppingListDto(id: fremdeListId, name: 'Gekapert', groupId: groupId))
        flushAndClear()
        def eigeneListen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
        def fremd = shoppingListRepository.findById(fremdeListId).get()
        def fremdName = fremd.name
        def fremdGroupId = fremd.group.id
        def fremdEintraege = fremd.shoppingItems*.id
        LOG.info('shoppingListService.addShoppingList(id {}, Gruppe {}) -> Liste {}; eigene Listen {}; Liste {} "{}" in Gruppe {} mit Einträgen {}',
                fremdeListId, groupId, result.id, eigeneListen*.id, fremdeListId, fremdName, fremdGroupId, fremdEintraege)

        then:
        1 * notificationService.sendShoppingListNotification(groupId, _, 'add')
        result.id != fremdeListId
        eigeneListen*.id == [result.id]
        eigeneListen*.name == ['Gekapert']
        fremdName == 'Fremd'
        fremdGroupId == andereGroupId
        fremdEintraege == [fremderEintragId]
        lastUpdateShoppingList(andereGroupId).time == START.time
    }

    private void ausfuehren(String aktion, Long groupId, Long listId) {
        switch (aktion) {
            case 'add':
                shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Neu', groupId: groupId))
                break
            case 'update':
                shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: listId, name: 'Neu', groupId: groupId))
                break
            case 'delete':
                shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: listId, groupId: groupId))
                break
            default:
                throw new IllegalArgumentException("Unbekannte Aktion $aktion")
        }
    }

    private Date lastUpdateShoppingList(Long groupId) {
        groupRepository.findById(groupId).get().lastUpdateShoppingList
    }
}
