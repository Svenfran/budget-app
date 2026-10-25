package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Fehlermeldungs-Vertrag beim Hinzufügen von Mitgliedern (Liste der Meldungen: {@link CartControllerApiSpec}) und
 * Rechte-Mapping (NotOwnerOfGroupException -> 403) für remove/change-owner/delete.
 */
class GroupControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(GroupControllerApiSpec)

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
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (group.service.ts)
        response.contentAsString.contains('niemand@divvy.test')
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
        response.contentAsString.contains('New member equals group owner')
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
        response.contentAsString.contains('Member already exists')
        0 * notificationService._
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

    private MockHttpServletResponse addNewMember(GroupScenario wg, User angemeldet, String newMemberEmail) {
        send(post('/api/groups/add-new-member'), user: angemeldet, body: [id: wg.group.id, newMemberEmail: newMemberEmail])
    }
}
