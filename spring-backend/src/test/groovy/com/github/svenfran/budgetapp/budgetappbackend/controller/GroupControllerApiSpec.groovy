package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * Fehlercode-Vertrag beim Hinzufügen von Mitgliedern mit Legacy-Text-Pins (Liste: {@link CartControllerApiSpec}) und
 * Rechte-Mapping (NotOwnerOfGroupException -> 403) für remove/change-owner/delete und JSON-Felder der Übersicht,
 * die das Frontend liest (ownerId, DIV-80).
 * Gruppenwährung (DIV-77): currencyCode in allen Gruppen-Antworten, hasBookings in der Übersicht, 400 bei unbekanntem
 * Code, 409 mit Code CURRENCY_CHANGE_CONFIRMATION_REQUIRED bei unbestätigtem Wechsel (Liste in
 * {@link CartControllerApiSpec}, Eintrag S1). Legacy-Content-Type der Text-Fehlerantwort (DIV-108).
 */
class GroupControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(GroupControllerApiSpec)

    /** Accept-Header, den der Angular-HttpClient der App standardmäßig sendet. */
    private static final String ANGULAR_ACCEPT = 'application/json, text/plain, */*'
    /**
     * Content-Type der Text-Fehlerantwort, wie ihn ältere App-Versionen bekommen (vor DIV-108 ermittelt): Spring wählt
     * bei diesem Accept application/json, der Body ist aber reiner Text. Angular scheitert am JSON-Parsen und liefert
     * den Text als String in err.error – genau darauf beruhen die Text-Auswertungen alter Apps.
     */
    private static final String LEGACY_CONTENT_TYPE = 'application/json'

    @Autowired ObjectMapper objectMapper

    def "Unbekannte E-Mail beim Hinzufügen: 404 mit der E-Mail in der Meldung"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Owner {}, neue Adresse niemand@divvy.test ohne Nutzer', wg.group.id, wg.owner.id)

        when:
        def response = addNewMember(wg, wg.owner, 'niemand@divvy.test')
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        // Legacy-Text (ältere App-Versionen erkennen den Fall per Substring der gesendeten Adresse)
        response.contentAsString == 'User with email niemand@divvy.test not found'
        0 * notificationService._
    }

    def "Owner fügt sich selbst hinzu: 403 'New member equals group owner'"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Owner {} fügt die eigene Adresse hinzu', wg.group.id, wg.owner.id)

        when:
        def response = addNewMember(wg, wg.owner, wg.owner.email)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
        response.contentAsString == 'New member equals group owner'
        0 * notificationService._
    }

    def "Bestehendes Mitglied erneut hinzufügen: 403 'Member already exists'"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {} wird erneut hinzugefügt', wg.group.id, wg.owner.id, ben.id)

        when:
        def response = addNewMember(wg, wg.owner, ben.email)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
        response.contentAsString == 'Member already exists'
        0 * notificationService._
    }

    def "Mit X-Error-Format json: #fall -> #status mit Code #code und bisherigem Text"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        if (fall == 'Währungswechsel ohne Bestätigung (S1)') fixtures.cart(wg)
        def request = fall == 'Währungswechsel ohne Bestätigung (S1)' ? put('/api/groups/update') : post('/api/groups/add-new-member')
        def body = fall == 'Währungswechsel ohne Bestätigung (S1)' ? ([id: wg.group.id, name: 'WG', currencyCode: 'USD'])
                : fall == 'unbekannte E-Mail (M2)' ? ([id: wg.group.id, newMemberEmail: 'niemand@divvy.test'])
                : fall == 'Owner fügt sich selbst hinzu (M3)' ? ([id: wg.group.id, newMemberEmail: wg.owner.email])
                : ([id: wg.group.id, newMemberEmail: ben.email])
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Mitglied {}; {}', wg.group.id, wg.owner.id, ben.id, fall)

        when:
        def response = send(request.header('X-Error-Format', 'json'), user: wg.owner, body: body)
        def antwort = json(response)
        LOG.info('Antwort: {} {} ({})', response.status, antwort, response.contentType)

        then:
        response.status == status
        response.contentType == 'application/json'
        antwort.code == code
        antwort.message == text
        !antwort.containsKey('details')
        0 * notificationService._

        where:
        fall                                    || status | code                                    | text
        'unbekannte E-Mail (M2)'                || 404    | 'USER_NOT_FOUND'                        | 'User with email niemand@divvy.test not found'
        'Owner fügt sich selbst hinzu (M3)'     || 403    | 'MEMBER_EQUALS_OWNER'                   | 'New member equals group owner'
        'Mitglied erneut hinzufügen (M4)'       || 403    | 'MEMBER_ALREADY_EXISTS'                 | 'Member already exists'
        'Währungswechsel ohne Bestätigung (S1)' || 409    | 'CURRENCY_CHANGE_CONFIRMATION_REQUIRED' | 'Changing the currency of a group with bookings requires confirmation'
    }

    def "Ohne X-Error-Format bleibt die Fehlerantwort Text wie für ältere App-Versionen (#status)"() {
        given: 'Accept wie der Angular-HttpClient der App'
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        if (status == 409) fixtures.cart(wg)
        def request = status == 409 || status == 400 ? put('/api/groups/update') : post('/api/groups/add-new-member')
        def body = status == 409 ? ([id: wg.group.id, name: 'WG', currencyCode: 'USD'])
                : status == 400 ? ([id: wg.group.id, name: 'WG', currencyCode: 'XXX'])
                : ([id: wg.group.id, newMemberEmail: status == 403 ? ben.email : 'niemand@divvy.test'])
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {}; erwartet {} ohne X-Error-Format', wg.group.id, wg.owner.id,
                ben.id, status)

        when:
        def response = send(request.header('Accept', ANGULAR_ACCEPT), user: wg.owner, body: body)
        LOG.info('Antwort: {} {} ({})', response.status, response.contentAsString, response.contentType)

        then:
        response.status == status
        response.contentAsString == text
        response.contentType == LEGACY_CONTENT_TYPE

        where:
        status || text
        400    || 'Currency XXX is not supported'
        403    || 'Member already exists'
        404    || 'User with email niemand@divvy.test not found'
        409    || 'Changing the currency of a group with bookings requires confirmation'
    }

    def "Mitglied ohne Owner-Rechte bei #aktion: 403"() {
        given: "Ben und Cleo sind Mitglieder; Ben versucht eine Owner-Aktion"
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def groupId = wg.group.id
        def request = aktion == 'Cleo entfernen' ? post('/api/groups/remove-member-from-group')
                : aktion == 'Owner an Cleo übergeben' ? post('/api/groups/change-groupowner')
                : delete('/api/groups/delete/{id}', groupId)
        // Map-Literale nach '?' in Klammern: sonst liest der Parser '? [' als Safe-Index-Operator '?['
        def body = aktion == 'Cleo entfernen' ? ([id: groupId, member: [id: cleo.id]])
                : aktion == 'Owner an Cleo übergeben' ? ([groupId: groupId, newOwner: [id: cleo.id]])
                : null
        def options = [user: ben] + (body != null ? ([body: body]) : [:])
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied Ben {} (angemeldet), Mitglied Cleo {}; Aktion: {}', groupId,
                wg.owner.id, ben.id, cleo.id, aktion)

        when:
        def response = send(options, request)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
        0 * notificationService._

        where:
        aktion << ['Cleo entfernen', 'Owner an Cleo übergeben', 'Gruppe löschen']
    }

    def "Übersicht liefert dem Mitglied ownerId und ownerName des Owners (Admin-Erkennung im Frontend, DIV-80)"() {
        given: "Anna ist Owner, Ben Mitglied und angemeldet"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def annaId = wg.owner.id
        def annaName = wg.owner.name
        def benId = ben.id
        LOG.info('Szenario: Gruppe {}, Owner {} ({}), Mitglied Ben {} (angemeldet)', groupId, annaId, annaName, benId)

        when:
        def response = send(get('/api/groups/overview'), user: ben)
        def eintrag = json(response).find { it.id == groupId }
        LOG.info('Antwort: Gruppe {} -> ownerId {}, ownerName {}, memberCount {}', eintrag?.id, eintrag?.ownerId,
                eintrag?.ownerName, eintrag?.memberCount)

        then:
        response.status == 200
        eintrag.ownerId == annaId
        eintrag.ownerId != benId
        eintrag.ownerName == annaName
        eintrag.memberCount == 1
    }

    // ----- Gruppenwährung (DIV-77) -----

    def "Sidenav, Übersicht und Mitgliederliste liefern currencyCode, die Übersicht hasBookings"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def leer = fixtures.group(owner: wg.owner)
        def (groupId, leerId) = [wg.group.id, leer.group.id]
        fixtures.cart(wg)
        flushAndClear()
        entityManager.createQuery('update Group g set g.currencyCode = :c where g.id = :id')
                .setParameter('c', SupportedCurrency.USD)
                .setParameter('id', groupId).executeUpdate()
        LOG.info('Szenario: Gruppe {} (USD, ein Einkauf) und Gruppe {} (EUR, leer), Owner {} (angemeldet), Mitglied {}',
                groupId, leerId, wg.owner.id, ben.id)

        when:
        def sidenav = json(send(get('/api/groups/sidenav'), user: wg.owner))
        def overview = json(send(get('/api/groups/overview'), user: wg.owner))
        def members = json(send(get('/api/groups/members/{id}', groupId), user: wg.owner))
        def sidenavCodes = [groupId, leerId].collect { id -> sidenav.find { it.id == id }?.currencyCode }
        def overviewWerte = [groupId, leerId].collect { id -> overview.find { it.id == id }?.with { [it.currencyCode, it.hasBookings] } }
        LOG.info('Antwort: sidenav {}, overview {}, members {}', sidenavCodes, overviewWerte, members.currencyCode)

        then:
        sidenavCodes == ['USD', 'EUR']
        overviewWerte == [['USD', true], ['EUR', false]]
        members.currencyCode == 'USD'
    }

    def "Gruppe anlegen mit #gesendet: #status, currencyCode #erwartet"() {
        given:
        def anna = fixtures.user()
        LOG.info('Szenario: Nutzer {} (angemeldet) legt eine Gruppe an, currencyCode {}', anna.id, gesendet)

        when:
        def response = send(post('/api/groups/add'), user: anna, body: [name: 'Reise'] + (gesendet == 'ohne Code' ? [:] : [currencyCode: gesendet]))
        def antwort = response.status == 201 ? json(response).currencyCode : response.contentAsString
        LOG.info('Antwort: {}', antwort)

        then:
        response.status == status
        antwort == erwartet

        where:
        gesendet    || status | erwartet
        'USD'       || 201    | 'USD'
        'ohne Code' || 201    | 'EUR'
        'JPY'       || 400    | 'Currency JPY is not supported'
    }

    def "Währung ändern: #fall -> #status"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        if (mitEinkauf) fixtures.cart(wg)
        def angemeldet = als == 'Owner' ? wg.owner : ben
        flushAndClear()
        LOG.info('Szenario: Gruppe {} (EUR, Einkauf {}), Owner {}, Mitglied {}; angemeldet {} ({}), Body {}', groupId,
                mitEinkauf, wg.owner.id, ben.id, angemeldet.id, als, body)

        when:
        def response = send(put('/api/groups/update'), user: angemeldet, body: [id: groupId, name: 'WG'] + body)
        def antwort = response.status == 200 ? json(response).currencyCode : response.contentAsString
        LOG.info('Antwort: {}', antwort)

        then:
        response.status == status
        antwort.contains(erwartet)

        where:
        fall                                | als        | mitEinkauf | body                                               || status | erwartet
        'leere Gruppe ohne Bestätigung'     | 'Owner'    | false      | [currencyCode: 'USD']                              || 200    | 'USD'
        'mit Einkauf ohne Bestätigung (S1)' | 'Owner'    | true       | [currencyCode: 'USD']                              || 409    | 'requires confirmation'
        'mit Einkauf und Bestätigung'       | 'Owner'    | true       | [currencyCode: 'USD', confirmCurrencyChange: true] || 200    | 'USD'
        'unbekannter Code'                  | 'Owner'    | false      | [currencyCode: 'XXX']                              || 400    | 'Currency XXX is not supported'
        'Mitglied statt Owner'              | 'Mitglied' | false      | [currencyCode: 'USD']                              || 403    | 'is not the owner of the Group'
    }

    private MockHttpServletResponse addNewMember(GroupScenario wg, User angemeldet, String newMemberEmail) {
        send(post('/api/groups/add-new-member'), user: angemeldet, body: [id: wg.group.id, newMemberEmail: newMemberEmail])
    }
}
