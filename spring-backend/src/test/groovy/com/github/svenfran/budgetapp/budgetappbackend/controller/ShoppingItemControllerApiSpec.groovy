package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * HTTP-Vertrag der Einträge von Einkaufslisten (Status, JSON-Form, Exception-Mapping). Zustand und Rechte im Detail:
 * service/ShoppingItemServiceIntegrationSpec. Das Frontend wertet keine Fehlertexte der Einkaufslisten aus
 * (kein Eintrag im Fehlermeldungs-Vertrag, siehe CartControllerApiSpec).
 */
class ShoppingItemControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingItemControllerApiSpec)

    @Autowired ObjectMapper objectMapper

    def "Eintrag anlegen: 201 mit id, name, completed, shoppingListId und groupId"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def (groupId, listId) = [wg.group.id, liste.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {}; neuer Eintrag "Milch"', groupId, ben.id, listId)

        when:
        def response = send(post('/api/groups/shopping-list/add-item'), user: ben,
                body: [id: null, name: 'Milch', completed: false, shoppingListId: listId, groupId: groupId])
        def body = json(response)
        LOG.info('Antwort: {}', body)

        then:
        response.status == 201
        body.keySet() == ['id', 'name', 'completed', 'shoppingListId', 'groupId'] as Set
        body.id != null
        body.name == 'Milch'
        body.completed == false
        body.shoppingListId == listId
        body.groupId == groupId
    }

    def "Eintrag ändern: 201 mit den geänderten Werten"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste, name: 'Milch', completed: false)
        def (groupId, listId, itemId) = [wg.group.id, liste.id, eintrag.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Eintrag {} "Milch" offen -> "Hafermilch" erledigt', groupId, ben.id, itemId)

        when:
        def response = send(put('/api/groups/shopping-list/update-item'), user: ben,
                body: [id: itemId, name: 'Hafermilch', completed: true, shoppingListId: listId, groupId: groupId])
        def body = json(response)
        LOG.info('Antwort: {}', body)

        then: 'Ist-Verhalten: Update antwortet mit 201, nicht 200'
        response.status == 201
        body.id == itemId
        body.name == 'Hafermilch'
        body.completed == true
        body.shoppingListId == listId
    }

    def "Eintrag löschen: 200 ohne Body"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste)
        def (groupId, listId, itemId) = [wg.group.id, liste.id, eintrag.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {}, Eintrag {} wird gelöscht', groupId, ben.id, listId, itemId)

        when:
        def response = send(post('/api/groups/shopping-list/delete-item'), user: ben,
                body: [id: itemId, shoppingListId: listId, groupId: groupId])
        LOG.info('Antwort: "{}"', response.contentAsString)

        then:
        response.status == 200
        response.contentAsString.isEmpty()
    }

    def "Erledigte Einträge löschen: 200 ohne Body"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: true)
        def brot = fixtures.shoppingItem(liste, completed: true)
        def (groupId, listId, milchId, brotId) = [wg.group.id, liste.id, milch.id, brot.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {}, erledigte Einträge {} und {}', groupId, ben.id, listId, milchId, brotId)

        when:
        def response = deleteAllItems(ben, [milchId, brotId].collect {
            [id: it, completed: true, shoppingListId: listId, groupId: groupId]
        })
        LOG.info('Antwort: "{}"', response.contentAsString)

        then:
        response.status == 200
        response.contentAsString.isEmpty()
    }

    def "Nicht-Mitglied erhält 403 beim Anlegen eines Eintrags"() {
        given:
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        def fremder = fixtures.user()
        LOG.info('Szenario: Gruppe {}, Owner {}, Liste {}; anfragender User {} ist kein Mitglied',
                wg.group.id, wg.owner.id, liste.id, fremder.id)

        when:
        def response = send(post('/api/groups/shopping-list/add-item'), user: fremder,
                body: [name: 'Milch', shoppingListId: liste.id, groupId: wg.group.id])
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
        0 * notificationService._
    }

    def "Ohne Anmeldung wird das Anlegen eines Eintrags mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        LOG.info('Szenario: Gruppe {}, Liste {}, Request ohne Anmeldung', wg.group.id, liste.id)

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(post('/api/groups/shopping-list/add-item'),
                body: [name: 'Milch', shoppingListId: liste.id, groupId: wg.group.id]).status == 403
    }

    def "Eintrag einer anderen Liste ändern: 404"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def andereListe = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(andereListe)
        def (groupId, listId, itemId) = [wg.group.id, liste.id, eintrag.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}; Eintrag {} gehört Liste {}, gesendet mit Liste {}',
                groupId, ben.id, itemId, andereListe.id, listId)

        when:
        def response = send(put('/api/groups/shopping-list/update-item'), user: ben,
                body: [id: itemId, name: 'Neu', completed: true, shoppingListId: listId, groupId: groupId])
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        response.contentAsString.contains('does not belong to shopping list')
        0 * notificationService._
    }

    def "Eintrag mit der ID eines fremden Eintrags anlegen: 201 mit neuer ID, der fremde Eintrag bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def fremderEintrag = fixtures.shoppingItem(fremdeListe, name: 'Fremd', completed: false)
        def (groupId, listId, andereGroupId, fremderEintragId) = [wg.group.id, liste.id, andereGruppe.group.id, fremderEintrag.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {}; Eintrag {} "Fremd" in Liste {} der Gruppe {} (Owner {}); Anlegen mit id {}',
                groupId, ben.id, listId, fremderEintragId, fremdeListe.id, andereGroupId, andereGruppe.owner.id, fremderEintragId)

        when:
        def response = send(post('/api/groups/shopping-list/add-item'), user: ben,
                body: [id: fremderEintragId, name: 'Gekapert', completed: true, shoppingListId: listId, groupId: groupId])
        def body = json(response)
        flushAndClear()
        def fremdeListen = json(send(get('/api/groups/shopping-lists-with-items/{groupId}', andereGroupId), user: andereGruppe.owner))
        def fremdeEintraege = fremdeListen[0].shoppingItems
        LOG.info('Antwort: {}; Einträge der Liste {}: {}', body, fremdeListe.id, fremdeEintraege)

        then:
        response.status == 201
        body.id != fremderEintragId
        body.shoppingListId == listId
        fremdeEintraege*.id == [fremderEintragId]
        fremdeEintraege[0].name == 'Fremd'
        fremdeEintraege[0].completed == false
    }

    def "Erledigte Einträge löschen mit unbekannter ID an zweiter Stelle: 404, nichts wird gelöscht"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def milch = fixtures.shoppingItem(liste, completed: true)
        def (groupId, listId, milchId) = [wg.group.id, liste.id, milch.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit erledigtem Eintrag {}; gesendet: {}, dann unbekannte ID {}',
                groupId, ben.id, listId, milchId, milchId, Long.MAX_VALUE)

        when: 'Long.MAX_VALUE statt einer kleinen ID, die Restdaten aus Commit-Specs treffen könnte'
        def response = deleteAllItems(ben, [milchId, Long.MAX_VALUE].collect {
            [id: it, completed: true, shoppingListId: listId, groupId: groupId]
        })
        flushAndClear()
        def eintraege = json(send(get('/api/groups/shopping-lists-with-items/{groupId}', groupId), user: ben))[0].shoppingItems
        LOG.info('Antwort: {}; Einträge der Liste {}: {}', response.contentAsString, listId, eintraege*.id)

        then:
        response.status == 404
        response.contentAsString.contains('not found')
        eintraege*.id == [milchId]
        0 * notificationService._
    }

    def "Einträge umsortieren: 200 mit groupId, shoppingListId, revision und normalisierter Reihenfolge, GET danach liefert sie"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def x = fixtures.shoppingItem(liste, sortOrder: 0)
        def y = fixtures.shoppingItem(liste, sortOrder: 1)
        def z = fixtures.shoppingItem(liste, sortOrder: 2)
        def (groupId, listId, xId, yId, zId) = [wg.group.id, liste.id, x.id, y.id, z.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit Einträgen {}; gewünscht {} (Y fehlt)',
                groupId, ben.id, listId, [xId, yId, zId], [zId, xId])

        when:
        def response = send(put('/api/groups/shopping-list/reorder-items'), user: ben,
                body: [groupId: groupId, shoppingListId: listId, shoppingItemIds: [zId, xId]])
        def body = json(response)
        flushAndClear()
        def liste0 = json(send(get('/api/groups/shopping-lists-with-items/{groupId}', groupId), user: ben))[0]
        LOG.info('Antwort: {}; GET danach: Einträge {}, itemOrderRevision {}', body, liste0.shoppingItems*.id, liste0.itemOrderRevision)

        then:
        response.status == 200
        body.keySet() == ['groupId', 'shoppingListId', 'revision', 'shoppingItemIds'] as Set
        body.groupId == groupId
        body.shoppingListId == listId
        body.revision == 1
        body.shoppingItemIds == [zId, xId, yId]
        liste0.shoppingItems*.id == [zId, xId, yId]
        liste0.itemOrderRevision == 1
        1 * notificationService.sendShoppingItemOrderNotification(groupId, _)
    }

    def "Einträge umsortieren #fall: #status"() {
        given:
        def ben = fixtures.user()
        def fremder = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereGruppe = fixtures.group()
        def liste = listeDerAnderenGruppe ? fixtures.shoppingList(andereGruppe) : fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste)
        def groupId = wg.group.id
        def angemeldet = [mitglied: ben, fremder: fremder][wer]
        LOG.info('Szenario: Gruppe {} (Mitglied {}, Nicht-Mitglied {}); Liste {} mit Eintrag {} gehört Gruppe {}; Request {}',
                groupId, ben.id, fremder.id, liste.id, eintrag.id, listeDerAnderenGruppe ? andereGruppe.group.id : groupId, fall)

        when:
        def request = put('/api/groups/shopping-list/reorder-items')
        def requestBody = [groupId: groupId, shoppingListId: liste.id, shoppingItemIds: [eintrag.id]]
        def response = angemeldet ? send(request, user: angemeldet, body: requestBody) : send(request, body: requestBody)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == status
        0 * notificationService._

        where:
        fall                         | wer        | listeDerAnderenGruppe || status
        'ohne Anmeldung'             | null       | false                 || 403
        'als Nicht-Mitglied'         | 'fremder'  | false                 || 403
        'mit Liste fremder Gruppe'   | 'mitglied' | true                  || 404
    }

    private MockHttpServletResponse deleteAllItems(User angemeldet, List<Map> items) {
        send(post('/api/groups/shopping-list/delete-all-items'), user: angemeldet, body: items)
    }
}
