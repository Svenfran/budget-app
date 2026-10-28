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

    @Autowired ObjectMapper objectMapper

    def "Mitglied erhält die Listen der Gruppe mit ihren Einträgen und den Reihenfolge-Revisionen als JSON"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg, name: 'Wocheneinkauf', itemOrderRevision: 3L)
        def milch = fixtures.shoppingItem(liste, name: 'Milch', completed: false)
        def brot = fixtures.shoppingItem(liste, name: 'Brot', completed: true)
        def (groupId, listId, milchId, brotId) = [wg.group.id, liste.id, milch.id, brot.id]
        flushAndClear()
        // listOrderRevision ist updatable = false und lässt sich nur per Update-Query setzen
        entityManager.createQuery('update Group g set g.listOrderRevision = 7 where g.id = :groupId')
                .setParameter('groupId', groupId).executeUpdate()
        LOG.info('Szenario: Gruppe {} (listOrderRevision 7), Mitglied {}, Liste {} "Wocheneinkauf" (itemOrderRevision 3) mit Einträgen {} "Milch" offen, {} "Brot" erledigt',
                groupId, ben.id, listId, milchId, brotId)

        when:
        def response = getShoppingLists(groupId, ben)
        def body = json(response)
        LOG.info('Antwort: Listen {}, Felder {}, Revisionen {}/{}, Einträge {}, Eintrag-Felder {}',
                body*.id, body[0].keySet(), body[0].listOrderRevision, body[0].itemOrderRevision,
                body[0].shoppingItems*.id, body[0].shoppingItems[0].keySet())

        then:
        response.status == 200
        body*.id == [listId]
        body[0].keySet() == ['id', 'name', 'shoppingItems', 'listOrderRevision', 'itemOrderRevision'] as Set
        body[0].name == 'Wocheneinkauf'
        body[0].listOrderRevision == 7
        body[0].itemOrderRevision == 3
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
        send(get('/api/groups/shopping-lists-with-items/{groupId}', wg.group.id)).status == 403
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

    def "Listen umsortieren: 200 mit groupId, revision und normalisierter Reihenfolge, GET danach liefert sie"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def a = fixtures.shoppingList(wg, sortOrder: 0)
        def b = fixtures.shoppingList(wg, sortOrder: 1)
        def c = fixtures.shoppingList(wg, sortOrder: 2)
        def (groupId, aId, bId, cId) = [wg.group.id, a.id, b.id, c.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen {}; gewünscht {} (B fehlt)', groupId, ben.id, [aId, bId, cId], [cId, aId])

        when:
        def response = send(put('/api/groups/shopping-list/reorder'), user: ben,
                body: [groupId: groupId, shoppingListIds: [cId, aId]])
        def body = json(response)
        flushAndClear()
        def listen = json(getShoppingLists(groupId, ben))
        LOG.info('Antwort: {}; GET danach: Listen {}, listOrderRevision {}', body, listen*.id, listen*.listOrderRevision)

        then:
        response.status == 200
        body.keySet() == ['groupId', 'revision', 'shoppingListIds'] as Set
        body.groupId == groupId
        body.revision == 1
        body.shoppingListIds == [cId, aId, bId]
        listen*.id == [cId, aId, bId]
        listen*.listOrderRevision == [1, 1, 1]
        1 * notificationService.sendShoppingListOrderNotification(groupId, _)
    }

    def "Listen umsortieren #fall: #status"() {
        given:
        def ben = fixtures.user()
        def fremder = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def groupId = unbekannteGruppe ? -1L : wg.group.id
        def angemeldet = [mitglied: ben, fremder: fremder][wer]
        LOG.info('Szenario: Gruppe {} mit Liste {}, Mitglied {}, Nicht-Mitglied {}; Request für Gruppe {} {}',
                wg.group.id, liste.id, ben.id, fremder.id, groupId, fall)

        when:
        def request = put('/api/groups/shopping-list/reorder')
        def requestBody = [groupId: groupId, shoppingListIds: [liste.id]]
        def response = angemeldet ? send(request, user: angemeldet, body: requestBody) : send(request, body: requestBody)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == status
        0 * notificationService._

        where:
        fall                | wer        | unbekannteGruppe || status
        'ohne Anmeldung'    | null       | false            || 403
        'als Nicht-Mitglied'| 'fremder'  | false            || 403
        'unbekannte Gruppe' | 'mitglied' | true             || 404
    }

    def "Zeitstempel älterer App-Versionen (#zeitstempel) wird ignoriert: sofort 200 mit den Listen, kein 304"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def (groupId, listId) = [wg.group.id, liste.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {}; requestTimeStamp {} ({})', groupId, ben.id, listId, requestTimeStamp, zeitstempel)

        when:
        def response = send(get('/api/groups/shopping-lists-with-items/{groupId}', groupId)
                .param('requestTimeStamp', requestTimeStamp as String), user: ben)
        def body = json(response)
        LOG.info('Antwort: Listen {}', body*.id)

        then:
        response.status == 200
        body*.id == [listId]

        where:
        zeitstempel                     | requestTimeStamp
        'bisheriger Frontend-Wert 1900' | DateUtils.toDate(LocalDate.of(1900, 1, 1)).time
        'in der Zukunft (früher 304)'   | DateUtils.toDate(LocalDate.of(2999, 12, 31)).time
    }

    // Einkaufsvorschläge (DIV-23)

    def "Vorschläge einer Liste mit Käufen: 200 mit groupId, shoppingListId, defaultSuggestions false und den Namen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        fixtures.purchase(liste, itemName: 'Milch', purchasedAt: DateUtils.toDate(LocalDate.of(2025, 6, 1)))
        fixtures.purchase(liste, itemName: 'Brot', purchasedAt: DateUtils.toDate(LocalDate.of(2025, 6, 2)))
        def (groupId, listId) = [wg.group.id, liste.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit Käufen Milch (01.06.), Brot (02.06.)', groupId, ben.id, listId)

        when:
        def response = getSuggestions(groupId, listId, ben)
        def body = json(response)
        LOG.info('Antwort: Felder {}, defaultSuggestions {}, names {}', body.keySet(), body.defaultSuggestions, body.names)

        then:
        response.status == 200
        body.keySet() == ['groupId', 'shoppingListId', 'defaultSuggestions', 'names'] as Set
        body.groupId == groupId
        body.shoppingListId == listId
        body.defaultSuggestions == false
        body.names == ['Brot', 'Milch']
    }

    def "Vorschläge einer Liste ohne Käufe: Standardvorschläge in der Sprache aus Accept-Language #sprache"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def (groupId, listId) = [wg.group.id, liste.id]
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} ohne Käufe und Einträge; Accept-Language {}', groupId, ben.id, listId, sprache)

        when:
        def response = send(get('/api/groups/shopping-list/suggestions/{groupId}/{shoppingListId}', groupId, listId)
                .header('Accept-Language', sprache), user: ben)
        def body = json(response)
        LOG.info('Antwort: defaultSuggestions {}, names {}', body.defaultSuggestions, body.names)

        then:
        response.status == 200
        body.defaultSuggestions == true
        body.names == erwartet

        where:
        sprache || erwartet
        'de'    || ['Milch', 'Tomaten', 'Äpfel', 'Joghurt', 'Beeren', 'Bananen', 'Avocado']
        'en'    || ['Milk', 'Tomatoes', 'Apples', 'Yogurt', 'Berries', 'Bananas', 'Avocado']
        'es'    || ['Leche', 'Tomates', 'Manzanas', 'Yogur', 'Bayas', 'Plátanos', 'Aguacate']
    }

    def "Nicht-Mitglied erhält 403 beim Abruf der Vorschläge"() {
        given:
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        def fremder = fixtures.user()
        LOG.info('Szenario: Gruppe {}, Liste {}, anfragender User {} ist kein Mitglied', wg.group.id, liste.id, fremder.id)

        when:
        def response = getSuggestions(wg.group.id, liste.id, fremder)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
    }

    def "Ohne Anmeldung wird der Abruf der Vorschläge mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        LOG.info('Szenario: Gruppe {}, Liste {}, Request ohne Anmeldung', wg.group.id, liste.id)

        expect:
        send(get('/api/groups/shopping-list/suggestions/{groupId}/{shoppingListId}', wg.group.id, liste.id)).status == 403
    }

    def "Vorschläge für die Liste einer fremden Gruppe: 404"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereGruppe = fixtures.group()
        def fremdeListe = fixtures.shoppingList(andereGruppe)
        def (groupId, fremdeListId) = [wg.group.id, fremdeListe.id]
        LOG.info('Szenario: Gruppe {}, Mitglied {}; Liste {} gehört Gruppe {}', groupId, ben.id, fremdeListId, andereGruppe.group.id)

        when:
        def response = getSuggestions(groupId, fremdeListId, ben)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        response.contentAsString.contains('does not belong to group')
    }

    private MockHttpServletResponse getSuggestions(Long groupId, Long listId, User angemeldet) {
        send(get('/api/groups/shopping-list/suggestions/{groupId}/{shoppingListId}', groupId, listId), user: angemeldet)
    }

    private MockHttpServletResponse getShoppingLists(Long groupId, User angemeldet) {
        send(get('/api/groups/shopping-lists-with-items/{groupId}', groupId), user: angemeldet)
    }
}
