package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.userdetails.User as SpringUser

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * DIV-22: E-Mail-Adressen sind unabhängig von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class AuthenticationControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationControllerApiSpec)

    static final String DEVICE = 'device-1'

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper
    @Autowired JwtService jwtService

    def "Registrierung speichert die E-Mail normalisiert und liefert sie so zurück"() {
        given:
        LOG.info('Szenario: neuer Nutzer max mit \' Max@Example.de \'')

        when:
        def response = register('max', ' Max@Example.de ')
        def body = json(response)
        LOG.info('Antwort: User {}, email {}', body.id, body.email)

        then:
        response.status == 200
        json(response).email == 'max@example.de'
        userRepository.findByEmail('max@example.de').isPresent()
    }

    def "Login mit abweichender Schreibweise ('#login') funktioniert"() {
        given:
        register('max', 'Max@Example.de')
        LOG.info('Szenario: registriert max mit Max@Example.de, Login mit \'{}\'', login)

        when:
        def response = authenticate(login)
        LOG.info('Antwort: email {}', json(response).email)

        then:
        response.status == 200
        json(response).email == 'max@example.de'

        where:
        login << ['max@example.de', 'MAX@EXAMPLE.DE', ' Max@Example.de ']
    }

    def "Registrierung mit einer nur in der Schreibweise abweichenden Adresse wird als 'existiert bereits' abgelehnt"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max mit max@example.de, dann maximilian mit Max@EXAMPLE.de')

        when:
        def response = register('maximilian', 'Max@EXAMPLE.de')
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 400
        // Frontend erkennt den Fall per Substring der gesendeten Adresse (auth.page.ts)
        response.contentAsString == 'User with email Max@EXAMPLE.de already exists'
    }

    def "Registrierung mit vergebenem Nutzernamen: 400 mit dem Namen in der Meldung"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max mit max@example.de, dann max mit moritz@example.de')

        when: 'andere E-Mail, sonst greift zuerst die E-Mail-Prüfung'
        def response = register('max', 'moritz@example.de')
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        // Frontend prüft status !== 403 und den Substring des gesendeten Namens (auth.page.ts)
        response.status == 400
        response.contentAsString == 'User with name max already exists'
    }

    def "Vor der Umstellung ausgestelltes Token mit abweichender Schreibweise im Subject bleibt gültig"() {
        given: 'Token wurde ausgestellt, als die Adresse noch als Alfred@Divvy.test gespeichert war'
        def alfred = fixtures.user(email: 'alfred@divvy.test')
        def jwt = jwtService.generateToken(userDetails('Alfred@Divvy.test'), DEVICE)
        def token = fixtures.token(alfred, token: jwt, deviceId: DEVICE)
        LOG.info('Szenario: Token {} für Subject Alfred@Divvy.test auf {}, Nutzer {} gespeichert als alfred@divvy.test',
                token.id, DEVICE, alfred.id)

        expect:
        jwtService.extractUsername(jwt) == 'Alfred@Divvy.test'
        callProtectedEndpoint(jwt).status == 200
    }

    def "Token eines nicht (mehr) existierenden Nutzers führt zu 403 statt Serverfehler"() {
        given:
        def anderer = fixtures.user()
        def jwt = jwtService.generateToken(userDetails('geloescht@divvy.test'), DEVICE)
        def token = fixtures.token(anderer, token: jwt, deviceId: DEVICE)
        LOG.info('Szenario: Token {} für Subject geloescht@divvy.test (kein Nutzer) auf {}, gespeichert bei Nutzer {}',
                token.id, DEVICE, anderer.id)

        expect:
        callProtectedEndpoint(jwt).status == 403
    }

    private MockHttpServletResponse register(String name, String email) {
        send(post('/api/auth/register'), body: [name: name, email: email, password: 'geheim123', deviceId: DEVICE])
    }

    private MockHttpServletResponse authenticate(String email) {
        send(post('/api/auth/authenticate'), body: [email: email, password: 'geheim123', deviceId: DEVICE])
    }

    private MockHttpServletResponse callProtectedEndpoint(String jwt) {
        send(get('/api/groups/sidenav')
                .header('Authorization', JwtService.BEARER_PREFIX + jwt)
                .header('deviceId', DEVICE))
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }
}
