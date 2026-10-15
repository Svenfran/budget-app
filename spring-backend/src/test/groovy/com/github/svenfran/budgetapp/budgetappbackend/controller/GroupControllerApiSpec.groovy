package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/** Fehlermeldungs-Vertrag beim Hinzufügen von Mitgliedern (Liste der Meldungen: {@link CartControllerApiSpec}). */
class GroupControllerApiSpec extends IntegrationSpec implements ApiRequests {

    @Autowired ObjectMapper objectMapper

    def "Unbekannte E-Mail beim Hinzufügen: 404 mit der E-Mail in der Meldung"() {
        given:
        def wg = fixtures.group()

        when:
        def response = addNewMember(wg, wg.owner, 'niemand@divvy.test')

        then:
        response.status == 404
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (group.service.ts)
        response.contentAsString.contains('niemand@divvy.test')
        0 * notificationService._
    }

    def "Owner fügt sich selbst hinzu: 403 'New member equals group owner'"() {
        given:
        def wg = fixtures.group()

        when:
        def response = addNewMember(wg, wg.owner, wg.owner.email)

        then:
        response.status == 403
        response.contentAsString.contains('New member equals group owner')
        0 * notificationService._
    }

    def "Bestehendes Mitglied erneut hinzufügen: 403 'Member already exists'"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])

        when:
        def response = addNewMember(wg, wg.owner, ben.email)

        then:
        response.status == 403
        response.contentAsString.contains('Member already exists')
        0 * notificationService._
    }

    private MockHttpServletResponse addNewMember(GroupScenario wg, User angemeldet, String newMemberEmail) {
        send(post('/api/groups/add-new-member'), user: angemeldet, body: [id: wg.group.id, newMemberEmail: newMemberEmail])
    }
}
