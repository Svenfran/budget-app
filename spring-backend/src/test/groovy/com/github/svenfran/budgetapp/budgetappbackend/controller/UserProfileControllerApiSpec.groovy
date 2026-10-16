package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * DIV-22: E-Mail ändern und Passwort zurücksetzen unabhängig von Groß-/Kleinschreibung und Leerzeichen.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class UserProfileControllerApiSpec extends IntegrationSpec implements ApiRequests {

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper

    def "E-Mail ändern auf eine nur in der Schreibweise abweichende fremde Adresse wird abgelehnt"() {
        given:
        fixtures.user(email: 'berta@example.de')
        def max = fixtures.user(email: 'max@example.de')

        when:
        def response = changeEmail(max, 'Berta@Example.DE')
        flushAndClear()

        then:
        response.status == 400
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (userprofile.service.ts)
        response.contentAsString == 'User with email Berta@Example.DE already exists'
        def email = userRepository.findById(max.id).get().email
        email == 'max@example.de'
    }

    def "E-Mail ändern auf die eigene Adresse in anderer Schreibweise ist ein No-Op"() {
        given:
        def max = fixtures.user(email: 'max@example.de')

        when:
        def response = changeEmail(max, ' MAX@Example.de ')
        flushAndClear()

        then:
        response.status == 201
        json(response).userEmail == 'max@example.de'
        def email = userRepository.findById(max.id).get().email
        email == 'max@example.de'
    }

    def "E-Mail ändern speichert die neue Adresse normalisiert"() {
        given:
        def max = fixtures.user(email: 'max@example.de')

        when:
        def response = changeEmail(max, ' Maximilian@Example.de')
        flushAndClear()

        then:
        response.status == 201
        json(response).userEmail == 'maximilian@example.de'
        def email = userRepository.findById(max.id).get().email
        email == 'maximilian@example.de'
    }

    def "Passwort-Reset mit '#eingabe' findet den Nutzer und schreibt an die gespeicherte Adresse"() {
        given:
        fixtures.user(email: 'max@example.de')

        when:
        def response = passwordReset(eingabe)

        then:
        response.status == 201
        1 * emailSenderService.sendEmail('max@example.de', _, _)

        where:
        eingabe << ['MAX@example.de', ' Max@Example.DE ']
    }

    def "Passwort-Reset für unbekannte Adresse: 404, Meldung enthält die Eingabe unverändert"() {
        when:
        def response = passwordReset('Niemand@Example.de')

        then:
        response.status == 404
        response.contentAsString == 'User with email Niemand@Example.de not found'
        0 * emailSenderService.sendEmail(*_)
    }

    def "Nutzername bereits vergeben: 400 mit dem Namen in der Meldung"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()

        when:
        def response = changeUserName(anna, ben.name)

        then:
        response.status == 400
        // Frontend erkennt den Fall per Substring des gesendeten Namens (userprofile.service.ts)
        response.contentAsString.contains(ben.name)
    }

    def "Reservierter Nutzername: 400 'User name is not allowed'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = changeUserName(anna, 'DELETED')

        then:
        response.status == 400
        response.contentAsString.contains('User name is not allowed')
    }

    def "Ungültige E-Mail: 400 exakt 'Invalid Email'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = changeEmail(anna, 'keine-mail')

        then:
        response.status == 400
        // Frontend vergleicht exakt (=== in userprofile.service.ts)
        response.contentAsString == 'Invalid Email'
    }

    def "Falsches altes Passwort: 400 exakt 'Incorrect password'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = updatePassword(anna, 'falsch', 'neu')

        then:
        response.status == 400
        // Frontend vergleicht exakt (=== in userprofile.service.ts)
        response.contentAsString == 'Incorrect password'
    }

    private MockHttpServletResponse changeUserName(User angemeldet, String newName) {
        send(put('/api/userprofile/update-username'), user: angemeldet, body: [id: angemeldet.id, userName: newName])
    }

    private MockHttpServletResponse updatePassword(User angemeldet, String oldPassword, String newPassword) {
        send(put('/api/userprofile/update-password'), user: angemeldet,
                body: [userId: angemeldet.id, oldPassword: oldPassword, newPassword: newPassword])
    }

    private MockHttpServletResponse changeEmail(User angemeldet, String newEmail) {
        send(put('/api/userprofile/update-usermail'), user: angemeldet,
                body: [id: angemeldet.id, userName: angemeldet.name, userEmail: newEmail])
    }

    private MockHttpServletResponse passwordReset(String email) {
        send(put('/api/userprofile/password-reset'), body: [email: email])
    }
}
