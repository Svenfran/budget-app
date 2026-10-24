package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

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
 * M1  cart.service.ts (add/update/settlement)        400 'not within membership period'           CartControllerApiSpec (alle drei)
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

    private static final Logger LOG = LoggerFactory.getLogger(CartControllerApiSpec)

    @Autowired ObjectMapper objectMapper

    def "Mitglied erhält die Einkäufe der Gruppe als JSON"() {
        given:
        def wg = fixtures.group()
        fixtures.cart(wg, title: 'Pizza', amount: 25d)
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf Pizza 25.00 vom Owner', wg.group.id, wg.owner.id)

        when:
        def response = send(get('/api/carts/carts-by-groupid/{id}', wg.group.id), user: wg.owner)
        def body = json(response)
        LOG.info('Antwort: {} Einkäufe, Titel {}, Beträge {}', body.size(), body*.title, body*.amount)

        then:
        response.status == 200
        body*.title == ['Pizza']
        body[0].amount == 25
        def (groupId, ownerName) = [wg.group.id, wg.owner.name]
        body[0].groupId == groupId
        body[0].userDto.userName == ownerName
    }

    def "Nicht-Mitglied erhält 403"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Owner {}, anfragender User ist kein Mitglied', wg.group.id, wg.owner.id)

        expect:
        send(get('/api/carts/carts-by-groupid/{id}', wg.group.id), user: fixtures.user()).status == 403
    }

    def "Ohne Anmeldung wird der Zugriff mit 403 abgelehnt"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Request ohne Anmeldung', wg.group.id)

        expect: 'Ist-Verhalten: Spring Security antwortet ohne Authentifizierung mit 403, nicht 401'
        send(get('/api/carts/carts-by-groupid/{id}', wg.group.id)).status == 403
    }

    def "Unbekannter Einkauf: 404"() {
        given:
        def angemeldet = fixtures.user()
        LOG.info('Szenario: angemeldeter User {}, Einkauf {} existiert nicht', angemeldet.id, Long.MAX_VALUE)

        when: 'Long.MAX_VALUE statt einer kleinen ID, die Restdaten aus Commit-Specs treffen könnte'
        def response = send(get('/api/carts/{id}', Long.MAX_VALUE), user: angemeldet)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 404
        response.contentAsString.contains('not found')
    }

    def "Kaufdatum vor Mitgliedschaftsbeginn: 400 mit der Meldung, die das Frontend auswertet"() {
        given: 'Mitgliedschaft beginnt 2024-06-15'
        def wg = fixtures.group()
        def body = [title: 'Alt', amount: 10, datePurchased: '2020-01-01', groupId: wg.group.id,
                    categoryDto: [id: wg.category.id]]
        LOG.info('Szenario: Gruppe {}, Owner {} (Mitglied seit 2024-06-15), Kaufdatum 2020-01-01', wg.group.id, wg.owner.id)

        when:
        def response = send(post('/api/carts/add'), user: wg.owner, body: body)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 400
        response.contentAsString.contains('not within membership period')
    }

    // ----- DIV-14: Löschen und Ausgleichszahlung auf HTTP-Ebene -----

    def "Löschen eines fremden Einkaufs: 403"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cart = fixtures.cart(wg, title: 'Pizza')
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {}; Einkauf {} vom Owner, Ben löscht', wg.group.id, wg.owner.id,
                ben.id, cart.id)

        when:
        def response = deleteCart(ben, cart.id)
        LOG.info('Antwort: {}', response.contentAsString)

        then: 'NotOwnerOfCartException wird auf 403 abgebildet'
        response.status == 403
        response.contentAsString.contains('is not the owner of the cart')
    }

    def "Eigentümer löscht seinen Einkauf: 200, danach 404 beim Abruf"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Pizza')
        LOG.info('Szenario: Gruppe {}, Owner {} (Eigentümer) löscht Einkauf {}', wg.group.id, wg.owner.id, cart.id)

        when:
        def deleted = deleteCart(wg.owner, cart.id)
        def abruf = send(get('/api/carts/{id}', cart.id), user: wg.owner)
        LOG.info('Antwort: Löschen {}, Abruf danach {}', deleted.status, abruf.status)

        then:
        deleted.status == 200
        abruf.status == 404
    }

    def "Ausgleichszahlung: 200 mit zwei Einkäufen als JSON"() {
        given:
        def anna = fixtures.user(name: "Anna${TestData.nextId()}")
        def ben = fixtures.user(name: "Ben${TestData.nextId()}")
        def wg = fixtures.group(owner: anna, members: [ben])
        def (annaId, benId, annaName, benName) = [anna.id, ben.id, anna.name, ben.name]
        def body = [amount: 30, groupId: wg.group.id, member: [id: benId], datePurchased: '2025-06-15']
        LOG.info('Szenario: Gruppe {}, Owner {} {}, Mitglied {} {}; Anna zahlt 30.00 an Ben, Accept-Language de', wg.group.id,
                annaId, annaName, benId, benName)

        when:
        def response = addSettlement(anna, body)
        def result = json(response)
        LOG.info('Antwort: Titel {}, Beträge {}, User {}, Kategorien {}', result*.title, result*.amount, result*.userDto*.id,
                result*.categoryDto*.name)

        then:
        response.status == 200
        result*.title == ["Ausgleichszahlung an ${benName}".toString(), "Ausgleichszahlung von ${annaName}".toString()]
        result*.amount == [30, -30]
        result*.userDto*.id == [annaId, benId]
        result*.categoryDto*.name == ['Ausgleichszahlung', 'Ausgleichszahlung']
        result.every { it.id != null }
    }

    def "Ausgleichszahlung an ein Nicht-Mitglied: 403"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        def body = [amount: 30, groupId: wg.group.id, member: [id: fremder.id], datePurchased: '2025-06-15']
        LOG.info('Szenario: Gruppe {}, Owner {} zahlt an User {} (kein Mitglied)', wg.group.id, wg.owner.id, fremder.id)

        when:
        def response = addSettlement(wg.owner, body)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 403
    }

    def "Kaufdatum vor Mitgliedschaftsbeginn bei #endpoint: 400 mit der Meldung, die das Frontend auswertet"() {
        given: 'Mitgliedschaften beginnen 2024-06-15, Kaufdatum 2020-01-01'
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cart = fixtures.cart(wg, title: 'Pizza', amount: 20d)
        def request = endpoint == 'PUT /api/carts/update' ? put('/api/carts/update') : post('/api/carts/settlement-payment/add')
        def body = endpoint == 'PUT /api/carts/update'
                ? [id: cart.id, title: 'Pizza', amount: 20, datePurchased: '2020-01-01', groupId: wg.group.id,
                   categoryDto: [id: wg.category.id]]
                : [amount: 30, groupId: wg.group.id, member: [id: ben.id], datePurchased: '2020-01-01']
        LOG.info('Szenario: Gruppe {}, Owner {} (Eigentümer von Einkauf {}), Mitglied {}; Kaufdatum 2020-01-01', wg.group.id,
                wg.owner.id, cart.id, ben.id)

        when:
        def response = send(request, user: wg.owner, body: body)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 400
        response.contentAsString.contains('not within membership period')

        where:
        endpoint << ['PUT /api/carts/update', 'POST /api/carts/settlement-payment/add']
    }

    private MockHttpServletResponse deleteCart(User user, Long cartId) {
        send(delete('/api/carts/delete/{id}', cartId), user: user)
    }

    /** Accept-Language de: Titel und Kategoriename der Ausgleichszahlung sind lokalisiert. */
    private MockHttpServletResponse addSettlement(User user, Map body) {
        send(post('/api/carts/settlement-payment/add').header('Accept-Language', 'de'), user: user, body: body)
    }
}
