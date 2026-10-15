package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.test.web.servlet.ResultActions

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * DIV-22: E-Mail-Adressen sind unabhängig von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class AuthenticationControllerApiSpec extends IntegrationSpec {

    static final String DEVICE = 'device-1'

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper
    @Autowired JwtService jwtService

    def "Registrierung speichert die E-Mail normalisiert und liefert sie so zurück"() {
        when:
        def response = register('max', ' Max@Example.de ')

        then:
        response.andReturn().response.status == 200
        json(response).email == 'max@example.de'
        userRepository.findByEmail('max@example.de').isPresent()
    }

    def "Login mit abweichender Schreibweise ('#login') funktioniert"() {
        given:
        register('max', 'Max@Example.de')

        when:
        def response = authenticate(login)

        then:
        response.andReturn().response.status == 200
        json(response).email == 'max@example.de'

        where:
        login << ['max@example.de', 'MAX@EXAMPLE.DE', ' Max@Example.de ']
    }

    def "Registrierung mit einer nur in der Schreibweise abweichenden Adresse wird als 'existiert bereits' abgelehnt"() {
        given:
        register('max', 'max@example.de')

        when:
        def response = register('maximilian', 'Max@EXAMPLE.de')

        then:
        response.andReturn().response.status == 400
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (auth.page.ts)
        response.andReturn().response.contentAsString == 'User with email Max@EXAMPLE.de already exists'
    }

    def "Registrierung mit vergebenem Nutzernamen: 400 mit dem Namen in der Meldung"() {
        given:
        register('max', 'max@example.de')

        when: 'andere E-Mail, sonst greift zuerst die E-Mail-Prüfung'
        def response = register('max', 'moritz@example.de')

        then:
        // Frontend prüft status !== 403 und den Substring des gesendeten Namens (auth.page.ts)
        response.andReturn().response.status == 400
        response.andReturn().response.contentAsString == 'User with name max already exists'
    }

    def "Vor der Umstellung ausgestelltes Token mit abweichender Schreibweise im Subject bleibt gültig"() {
        given: 'Token wurde ausgestellt, als die Adresse noch als Alfred@Divvy.test gespeichert war'
        def alfred = fixtures.user(email: 'alfred@divvy.test')
        def jwt = jwtService.generateToken(userDetails('Alfred@Divvy.test'), DEVICE)
        fixtures.token(alfred, token: jwt, deviceId: DEVICE)

        expect:
        jwtService.extractUsername(jwt) == 'Alfred@Divvy.test'
        callProtectedEndpoint(jwt).andReturn().response.status == 200
    }

    def "Token eines nicht (mehr) existierenden Nutzers führt zu 403 statt Serverfehler"() {
        given:
        def anderer = fixtures.user()
        def jwt = jwtService.generateToken(userDetails('geloescht@divvy.test'), DEVICE)
        fixtures.token(anderer, token: jwt, deviceId: DEVICE)

        expect:
        callProtectedEndpoint(jwt).andReturn().response.status == 403
    }

    private ResultActions register(String name, String email) {
        mockMvc.perform(post('/api/auth/register')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([name: name, email: email, password: 'geheim123', deviceId: DEVICE])))
    }

    private ResultActions authenticate(String email) {
        mockMvc.perform(post('/api/auth/authenticate')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString([email: email, password: 'geheim123', deviceId: DEVICE])))
    }

    private ResultActions callProtectedEndpoint(String jwt) {
        mockMvc.perform(get('/api/groups/sidenav')
                .header('Authorization', JwtService.BEARER_PREFIX + jwt)
                .header('deviceId', DEVICE))
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }

    private Map json(ResultActions response) {
        objectMapper.readValue(response.andReturn().response.contentAsString, Map)
    }
}
