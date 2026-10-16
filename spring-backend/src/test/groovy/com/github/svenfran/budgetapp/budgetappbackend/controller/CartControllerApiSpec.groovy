package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Referenz-API-Spec (MockMvc): HTTP-Status, Exception-Mapping von {@link RestResponseEntityExceptionHandler},
 * JSON-Form der DTOs, Security-Regeln und Fehlermeldungs-Vertrag zum Frontend.
 *
 * - Requests über ApiRequests.send (Anmeldung per user: → .with(user(entity))); ohne Anmeldung antwortet Spring Security mit 403, nicht 401.
 * - JSON über den Jackson-ObjectMapper und Groovy-Vergleiche statt jsonPath.
 *
 * Fehlermeldungs-Vertrag (Übergangslösung bis zur Entscheidung über Fehlercodes, Konzept Q4):
 * Das Frontend erkennt Fehlerfälle am Text der Backend-Meldung. Jede ausgewertete Meldung hat einen API-Test.
 * <pre>
 * #   Frontend-Stelle                                Erwartet (Status, Text)                      Test
 * M1  cart.service.ts (add/update/settlement)        400 'not within membership period'           CartControllerApiSpec
 * M2  group.service.ts addMemberToGroup              404 enthält gesendete E-Mail                 GroupControllerApiSpec
 * M3  group.service.ts addMemberToGroup              403 'New member equals group owner'          GroupControllerApiSpec
 * M4  group.service.ts addMemberToGroup              403 'Member already exists'                  GroupControllerApiSpec
 * M5  auth.page.ts Registrierung (status != 403)     400 enthält gesendete E-Mail                 AuthenticationControllerApiSpec
 * M6  auth.page.ts Registrierung (status != 403)     400 enthält gesendeten Namen                 AuthenticationControllerApiSpec
 * M7  userprofile.service.ts changeUserName          400 enthält gesendeten Namen                 UserProfileControllerApiSpec
 * M8  userprofile.service.ts changeUserName          400 'User name is not allowed'               UserProfileControllerApiSpec
 * M9  userprofile.service.ts changeUserEmail         400 enthält gesendete E-Mail                 UserProfileControllerApiSpec
 * M10 userprofile.service.ts resetPassword           404 enthält gesendete E-Mail                 UserProfileControllerApiSpec
 * X1  userprofile.service.ts (=== exakt)             400 'Invalid Email'                          UserProfileControllerApiSpec
 * X2  userprofile.service.ts changePassword (===)    400 'Incorrect password'                     UserProfileControllerApiSpec
 * </pre>
 * Nicht abgedeckt: Bei parallelen Registrierungen/E-Mail-Änderungen liefert der Unique-Constraint-Handler
 * 'User with email already exists' ohne Adresse; das Frontend erkennt diesen Fall nicht (bekannt seit DIV-22).
 */
class CartControllerApiSpec extends IntegrationSpec implements ApiRequests {

    @Autowired ObjectMapper objectMapper

    def "Mitglied erhält die Einkäufe der Gruppe als JSON"() {
        given:
        def wg = fixtures.group()
        fixtures.cart(wg, title: 'Pizza', amount: 25d)

        when:
        def response = send(get('/api/carts/carts-by-groupid/{id}', wg.group.id), user: wg.owner)

        then:
        response.status == 200
        def body = json(response)
        body*.title == ['Pizza']
        body[0].amount == 25
        def (groupId, ownerName) = [wg.group.id, wg.owner.name]
        body[0].groupId == groupId
        body[0].userDto.userName == ownerName
    }

    def "Nicht-Mitglied erhält 403"() {
        given:
        def wg = fixtures.group()

        expect:
        send(get('/api/carts/carts-by-groupid/{id}', wg.group.id), user: fixtures.user()).status == 403
    }

    def "Ohne Anmeldung wird der Zugriff mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(get('/api/carts/carts-by-groupid/{id}', wg.group.id)).status == 403
    }

    def "Unbekannter Einkauf: 404"() {
        given:
        def angemeldet = fixtures.user()

        when: 'Long.MAX_VALUE statt einer kleinen ID, die Restdaten aus Commit-Specs treffen könnte'
        def response = send(get('/api/carts/{id}', Long.MAX_VALUE), user: angemeldet)

        then:
        response.status == 404
        response.contentAsString.contains('not found')
    }

    def "Kaufdatum vor Mitgliedschaftsbeginn: 400 mit der Meldung, die das Frontend auswertet"() {
        given: 'Mitgliedschaft beginnt 2024-06-15'
        def wg = fixtures.group()
        def body = [title: 'Alt', amount: 10, datePurchased: '2020-01-01', groupId: wg.group.id,
                    categoryDto: [id: wg.category.id]]

        when:
        def response = send(post('/api/carts/add'), user: wg.owner, body: body)

        then:
        response.status == 400
        response.contentAsString.contains('not within membership period')
    }
}
