package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * DIV-22: E-Mail ändern und Passwort zurücksetzen unabhängig von Groß-/Kleinschreibung und Leerzeichen.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class UserProfileControllerApiSpec extends IntegrationSpec {

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
        response.andReturn().response.status == 400
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (userprofile.service.ts)
        response.andReturn().response.contentAsString == 'User with email Berta@Example.DE already exists'
        userRepository.findById(max.id).get().email == 'max@example.de'
    }

    def "E-Mail ändern auf die eigene Adresse in anderer Schreibweise ist ein No-Op"() {
        given:
        def max = fixtures.user(email: 'max@example.de')

        when:
        def response = changeEmail(max, ' MAX@Example.de ')
        flushAndClear()

        then:
        response.andReturn().response.status == 201
        json(response).userEmail == 'max@example.de'
        userRepository.findById(max.id).get().email == 'max@example.de'
    }

    def "E-Mail ändern speichert die neue Adresse normalisiert"() {
        given:
        def max = fixtures.user(email: 'max@example.de')

        when:
        def response = changeEmail(max, ' Maximilian@Example.de')
        flushAndClear()

        then:
        response.andReturn().response.status == 201
        json(response).userEmail == 'maximilian@example.de'
        userRepository.findById(max.id).get().email == 'maximilian@example.de'
    }

    def "Passwort-Reset mit '#eingabe' findet den Nutzer und schreibt an die gespeicherte Adresse"() {
        given:
        fixtures.user(email: 'max@example.de')

        when:
        def response = passwordReset(eingabe)

        then:
        response.andReturn().response.status == 201
        1 * emailSenderService.sendEmail('max@example.de', _, _)

        where:
        eingabe << ['MAX@example.de', ' Max@Example.DE ']
    }

    def "Passwort-Reset für unbekannte Adresse: 404, Meldung enthält die Eingabe unverändert"() {
        when:
        def response = passwordReset('Niemand@Example.de')

        then:
        response.andReturn().response.status == 404
        response.andReturn().response.contentAsString == 'User with email Niemand@Example.de not found'
        0 * emailSenderService.sendEmail(*_)
    }

    def "Nutzername bereits vergeben: 400 mit dem Namen in der Meldung"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()

        when:
        def response = changeUserName(anna, ben.name)

        then:
        response.andReturn().response.status == 400
        // Frontend erkennt den Fall per Substring des gesendeten Namens (userprofile.service.ts)
        response.andReturn().response.contentAsString.contains(ben.name)
    }

    def "Reservierter Nutzername: 400 'User name is not allowed'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = changeUserName(anna, 'DELETED')

        then:
        response.andReturn().response.status == 400
        response.andReturn().response.contentAsString.contains('User name is not allowed')
    }

    def "Ungültige E-Mail: 400 exakt 'Invalid Email'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = changeEmail(anna, 'keine-mail')

        then:
        response.andReturn().response.status == 400
        // Frontend vergleicht exakt (=== in userprofile.service.ts)
        response.andReturn().response.contentAsString == 'Invalid Email'
    }

    def "Falsches altes Passwort: 400 exakt 'Incorrect password'"() {
        given:
        def anna = fixtures.user()

        when:
        def response = updatePassword(anna, 'falsch', 'neu')

        then:
        response.andReturn().response.status == 400
        // Frontend vergleicht exakt (=== in userprofile.service.ts)
        response.andReturn().response.contentAsString == 'Incorrect password'
    }

    private ResultActions changeUserName(User angemeldet, String newName) {
        mockMvc.perform(put('/api/userprofile/update-username')
                .with(user(angemeldet))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([id: angemeldet.id, userName: newName])))
    }

    private ResultActions updatePassword(User angemeldet, String oldPassword, String newPassword) {
        mockMvc.perform(put('/api/userprofile/update-password')
                .with(user(angemeldet))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([userId: angemeldet.id, oldPassword: oldPassword, newPassword: newPassword])))
    }

    private ResultActions changeEmail(User angemeldet, String newEmail) {
        mockMvc.perform(put('/api/userprofile/update-usermail')
                .with(user(angemeldet))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([id: angemeldet.id, userName: angemeldet.name, userEmail: newEmail])))
    }

    private ResultActions passwordReset(String email) {
        mockMvc.perform(put('/api/userprofile/password-reset')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([email: email])))
    }

    private Map json(ResultActions response) {
        objectMapper.readValue(response.andReturn().response.contentAsString, Map)
    }
}
