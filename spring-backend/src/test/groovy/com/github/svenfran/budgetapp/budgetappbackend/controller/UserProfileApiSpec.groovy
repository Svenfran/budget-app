package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/** DIV-22: E-Mail ändern und Passwort zurücksetzen unabhängig von Groß-/Kleinschreibung und Leerzeichen. */
class UserProfileApiSpec extends IntegrationSpec {

    static final String DEVICE = 'device-1'

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper

    def "E-Mail ändern auf eine nur in der Schreibweise abweichende fremde Adresse wird abgelehnt"() {
        given:
        register('berta', 'berta@example.de')
        def max = register('max', 'max@example.de')

        when:
        def response = changeEmail(max, 'Berta@Example.DE')

        then:
        response.andReturn().response.status == 400
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (userprofile.service.ts)
        response.andReturn().response.contentAsString == 'User with email Berta@Example.DE already exists'
        userRepository.findById(max.id as Long).get().email == 'max@example.de'
    }

    def "E-Mail ändern auf die eigene Adresse in anderer Schreibweise ist ein No-Op"() {
        given:
        def max = register('max', 'max@example.de')

        when:
        def response = changeEmail(max, ' MAX@Example.de ')

        then:
        response.andReturn().response.status == 201
        json(response).userEmail == 'max@example.de'
        userRepository.findById(max.id as Long).get().email == 'max@example.de'
    }

    def "E-Mail ändern speichert die neue Adresse normalisiert"() {
        given:
        def max = register('max', 'max@example.de')

        when:
        def response = changeEmail(max, ' Maximilian@Example.de')

        then:
        response.andReturn().response.status == 201
        json(response).userEmail == 'maximilian@example.de'
        userRepository.findById(max.id as Long).get().email == 'maximilian@example.de'
    }

    def "Passwort-Reset mit '#eingabe' findet den Nutzer und schreibt an die gespeicherte Adresse"() {
        given:
        register('max', 'max@example.de')

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

    /** Registriert über die API und liefert die AuthenticationResponse (id, name, email, token, ...). */
    private Map register(String name, String email) {
        def response = mockMvc.perform(post('/api/auth/register')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([name: name, email: email, password: 'geheim123', deviceId: DEVICE])))
        assert response.andReturn().response.status == 200
        json(response)
    }

    private ResultActions changeEmail(Map user, String newEmail) {
        mockMvc.perform(put('/api/userprofile/update-usermail')
                .header('Authorization', JwtService.BEARER_PREFIX + user.token)
                .header('deviceId', DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([id: user.id, userName: user.name, userEmail: newEmail])))
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
