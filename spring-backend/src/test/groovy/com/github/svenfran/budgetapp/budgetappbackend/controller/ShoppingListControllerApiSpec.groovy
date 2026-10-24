package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import java.time.LocalDate

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * HTTP-Vertrag der Einkaufslisten (Status, JSON-Form, Exception-Mapping). Zustand und Rechte im Detail:
 * service/ShoppingListServiceIntegrationSpec. Das Frontend wertet keine Fehlertexte der Einkaufslisten aus
 * (kein Eintrag im Fehlermeldungs-Vertrag, siehe CartControllerApiSpec).
 *
 * Fixtures.shoppingItem trägt den Eintrag nicht in list.shoppingItems ein: vor Requests, die die Einträge einer
 * Liste lesen, flushAndClear().
 */
class ShoppingListControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingListControllerApiSpec)

    /** Zeitstempel, den das Frontend immer sendet (shoppinglist.service.ts, INITIAL_REQUEST_TIMESTAMP). */
    private static final long FRONTEND_TIMESTAMP = DateUtils.toDate(LocalDate.of(1900, 1, 1)).time

    @Autowired ObjectMapper objectMapper

    def "Mitglied erhält die Listen der Gruppe mit ihren Einträgen als JSON"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg, name: 'Wocheneinkauf')
        def milch = fixtures.shoppingItem(liste, name: 'Milch', completed: false)
        def brot = fixtures.shoppingItem(liste, name: 'Brot', completed: true)
        def (groupId, listId, milchId, brotId) = [wg.group.id, liste.id, milch.id, brot.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} "Wocheneinkauf" mit Einträgen {} "Milch" offen, {} "Brot" erledigt',
                groupId, ben.id, listId, milchId, brotId)

        when:
        def response = getShoppingLists(groupId, ben)
        def body = json(response)
        LOG.info('Antwort: Listen {}, Felder {}, Einträge {}, Eintrag-Felder {}',
                body*.id, body[0].keySet(), body[0].shoppingItems*.id, body[0].shoppingItems[0].keySet())

        then:
        response.status == 200
        body*.id == [listId]
        body[0].keySet() == ['id', 'name', 'shoppingItems'] as Set
        body[0].name == 'Wocheneinkauf'
        body[0].shoppingItems*.id == [milchId, brotId]
        body[0].shoppingItems[0].keySet() == ['id', 'name', 'completed'] as Set
        body[0].shoppingItems*.name == ['Milch', 'Brot']
        body[0].shoppingItems*.completed == [false, true]
    }

    def "Gruppe ohne Listen: 200 mit leerem Array"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        LOG.info('Szenario: Gruppe {} ohne Einkaufslisten, Mitglied {}', wg.group.id, ben.id)

        when:
        def response = getShoppingLists(wg.group.id, ben)
        def body = json(response)
        LOG.info('Antwort: {}', body)

        then:
        response.status == 200
        body == []
    }

    def "Liste anlegen: 201 mit id, name und groupId"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        LOG.info('Szenario: Gruppe {}, Mitglied {} legt "Wocheneinkauf" an', groupId, ben.id)

        when:
        def response = send(post('/api/groups/shopping-list/add'), user: ben,
                body: [id: null, name: 'Wocheneinkauf', groupId: groupId])
        def body = json(response)
        LOG.info('Antwort: {}', body)

        then:
        response.status == 201
        body.keySet() == ['id', 'name', 'groupId'] as Set
        body.id != null
        body.name == 'Wocheneinkauf'
        body.groupId == groupId
    }

    def "Liste umbenennen: 201 mit dem neuen Namen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg, name: 'Alt')
        def (groupId, listId) = [wg.group.id, liste.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} "Alt" -> "Neu"', groupId, ben.id, listId)

        when:
        def response = send(put('/api/groups/shopping-list/update'), user: ben,
                body: [id: listId, name: 'Neu', groupId: groupId])
        def body = json(response)
        LOG.info('Antwort: {}', body)

        then: 'Ist-Verhalten: Update antwortet mit 201, nicht 200'
        response.status == 201
        body.id == listId
        body.name == 'Neu'
        body.groupId == groupId
    }

    def "Liste löschen: 200 ohne Body"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        fixtures.shoppingItem(liste)
        def (groupId, listId) = [wg.group.id, liste.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit einem Eintrag wird gelöscht', groupId, ben.id, listId)

        when:
        def response = send(post('/api/groups/shopping-list/delete'), user: ben, body: [id: listId, groupId: groupId])
        LOG.info('Antwort: "{}"', response.contentAsString)

        then:
        response.status == 200
        response.contentAsString.isEmpty()
    }

    def "Nicht-Mitglied erhält 403 beim Laden der Listen"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        LOG.info('Szenario: Gruppe {}, Owner {}, anfragender User {} ist kein Mitglied', wg.group.id, wg.owner.id, fremder.id)

        when:
        def response = getShoppingLists(wg.group.id, fremder)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
        0 * notificationService._
    }

    def "Ohne Anmeldung wird das Laden der Listen mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Request ohne Anmeldung', wg.group.id)

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(get('/api/groups/shopping-lists-with-items/{groupId}', wg.group.id)
                .param('requestTimeStamp', FRONTEND_TIMESTAMP as String)).status == 403
    }

    def "Liste einer fremden Gruppe umbenennen: 404"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe, name: 'Fremd')
        def (groupId, fremdeListId) = [wg.group.id, fremdeListe.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}; Liste {} gehört Gruppe {}', groupId, ben.id, fremdeListId, andereGruppe.group.id)

        when:
        def response = send(put('/api/groups/shopping-list/update'), user: ben,
                body: [id: fremdeListId, name: 'Neu', groupId: groupId])
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        response.contentAsString.contains('does not belong to group')
        0 * notificationService._
    }

    def "Liste mit der ID einer fremden Liste anlegen: 201 mit neuer ID, die fremde Liste bleibt unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe, name: 'Fremd')
        def fremderEintrag = fixtures.shoppingItem(fremdeListe, name: 'Kaffee')
        def (groupId, andereGroupId, fremdeListId, fremderEintragId) = [wg.group.id, andereGruppe.group.id, fremdeListe.id, fremderEintrag.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}; Liste {} "Fremd" mit Eintrag {} gehört Gruppe {} (Owner {}); Anlegen mit id {}',
                groupId, ben.id, fremdeListId, fremderEintragId, andereGroupId, andereGruppe.owner.id, fremdeListId)

        when:
        def response = send(post('/api/groups/shopping-list/add'), user: ben,
                body: [id: fremdeListId, name: 'Gekapert', groupId: groupId])
        def body = json(response)
        flushAndClear()
        def fremdeListen = json(getShoppingLists(andereGroupId, andereGruppe.owner))
        LOG.info('Antwort: {}; Listen der Gruppe {}: {}', body, andereGroupId, fremdeListen.collect { [it.id, it.name, it.shoppingItems*.id] })

        then:
        response.status == 201
        body.id != fremdeListId
        body.groupId == groupId
        fremdeListen*.id == [fremdeListId]
        fremdeListen[0].name == 'Fremd'
        fremdeListen[0].shoppingItems*.id == [fremderEintragId]
    }

    private MockHttpServletResponse getShoppingLists(Long groupId, User angemeldet) {
        send(get('/api/groups/shopping-lists-with-items/{groupId}', groupId)
                .param('requestTimeStamp', FRONTEND_TIMESTAMP as String), user: angemeldet)
    }
}
