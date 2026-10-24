package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.userdetails.User as SpringUser

import java.time.LocalDate

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * DIV-22: E-Mail-Adressen sind unabhängig von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * DIV-18: JWT-Filter Ende-zu-Ende – widerrufene/abgelaufene Tokens (DB-Flags und JWT-exp), falsche DeviceId,
 * Logout, nicht verwertbare JWTs (403 statt Serverfehler) und öffentliche Endpunkte mit ungültigem Bearer-Header.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class AuthenticationControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationControllerApiSpec)

    static final String DEVICE = 'device-1'
    /** Dummy-Secret (Base64, 45 Byte), das nicht dem Test-Secret entspricht. */
    static final String FREMDER_SECRET = 'ZGl2dnktZnJlbWRlci1kdW1teS1zY2hsdWVzc2VsLW51ci1mdWVyLXRlc3Rz'
    static final Date ABGELAUFEN_AM = DateUtils.toDate(LocalDate.of(2025, 1, 1))
    static final Date AUSGESTELLT_AM = DateUtils.toDate(LocalDate.of(2024, 12, 11))

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper
    @Autowired JwtService jwtService
    @Value('${secret_key}') String secretKey

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

    def "Gespeicherter Token mit expired=#expired, revoked=#revoked wird mit 403 abgelehnt"() {
        given:
        def alfred = fixtures.user()
        def jwt = jwtService.generateToken(alfred, DEVICE)
        def token = fixtures.token(alfred, token: jwt, deviceId: DEVICE, expired: expired, revoked: revoked)
        LOG.info('Szenario: Token {} von Nutzer {} auf {}, expired {}, revoked {}', token.id, alfred.id, DEVICE, expired, revoked)

        expect:
        callProtectedEndpoint(jwt).status == 403

        where:
        expired | revoked
        true    | false
        false   | true
        true    | true
    }

    def "Falsche DeviceId (#fall) führt zu 403"() {
        given:
        def alfred = fixtures.user()
        def jwt = jwtService.generateToken(alfred, claimDevice)
        def token = fixtures.token(alfred, token: jwt, deviceId: gespeichertesDevice)
        LOG.info('Szenario: Token {} von Nutzer {}, DeviceId im Claim {}, gespeichert {}, im Header {}',
                token.id, alfred.id, claimDevice, gespeichertesDevice, headerDevice)

        expect:
        callProtectedEndpoint(jwt, headerDevice).status == 403

        where:
        fall                              | claimDevice | gespeichertesDevice | headerDevice
        'Header weicht ab'                | DEVICE      | DEVICE              | 'device-2'
        'Header fehlt'                    | DEVICE      | DEVICE              | null
        'gespeicherte DeviceId weicht ab' | DEVICE      | 'device-2'          | DEVICE
        'Claim weicht ab'                 | 'device-2'  | DEVICE              | DEVICE
    }

    def "Mit dem beim Login ausgegebenen Token ist ein geschützter Request erlaubt"() {
        given:
        register('max', 'max@example.de')
        def login = json(authenticate('max@example.de'))
        LOG.info('Szenario: registriert max mit max@example.de, Login auf {} liefert Token für Nutzer {}', DEVICE, login.id)

        expect:
        callProtectedEndpoint(login.token).status == 200
    }

    def "Nach dem Logout wird derselbe Token mit 403 abgelehnt"() {
        given:
        def registrierung = json(register('max', 'max@example.de'))
        def jwt = registrierung.token
        LOG.info('Szenario: registriert max mit max@example.de, Token für Nutzer {} auf {}', registrierung.id, DEVICE)

        when:
        def vorher = callProtectedEndpoint(jwt).status
        def abgemeldet = logout(jwt).status
        def nachher = callProtectedEndpoint(jwt).status
        LOG.info('Antwort: vor dem Logout {}, Logout {}, nach dem Logout {}', vorher, abgemeldet, nachher)

        then:
        vorher == 200
        abgemeldet == 200
        nachher == 403
    }

    def "Nicht verwertbares JWT (#fall) führt zu 403 statt Serverfehler"() {
        given:
        def alfred = fixtures.user()
        def jwt = [
                'abgelaufen'    : { TestData.jwt(secret: secretKey, subject: alfred.email, deviceId: DEVICE,
                                                 issuedAt: AUSGESTELLT_AM, expiration: ABGELAUFEN_AM) },
                'fremd signiert': { TestData.jwt(secret: FREMDER_SECRET, subject: alfred.email, deviceId: DEVICE,
                                                 expiration: DateUtils.toDate(DateUtils.NO_END_DATE)) },
                'unlesbar'      : { 'kein-jwt' },
                'leer'          : { '' },
        ][fall]()
        // unlesbar/leer ohne DB-Zeile: das JWT wird vor dem Token-Lookup geparst
        def tokenId = gespeichert ? fixtures.token(alfred, token: jwt, deviceId: DEVICE).id : null
        LOG.info('Szenario: JWT {} für Nutzer {} auf {}, gespeichert als Token {}', fall, alfred.id, DEVICE, tokenId)

        expect:
        callProtectedEndpoint(jwt).status == 403

        where:
        fall             | gespeichert
        'abgelaufen'     | true
        'fremd signiert' | true
        'unlesbar'       | false
        'leer'           | false
    }

    def "Login mit abgelaufenem Bearer-Header liefert 200 und einen verwendbaren Token"() {
        given:
        def max = json(register('max', 'max@example.de'))
        def abgelaufen = TestData.jwt(secret: secretKey, subject: 'max@example.de', deviceId: DEVICE,
                issuedAt: AUSGESTELLT_AM, expiration: ABGELAUFEN_AM)
        LOG.info('Szenario: registriert max (Nutzer {}), Login auf {} mit abgelaufenem Bearer-Token für max (exp 2025-01-01)',
                max.id, DEVICE)

        when:
        def response = authenticate('max@example.de', abgelaufen)
        def neuerToken = json(response).token
        def geschuetzt = callProtectedEndpoint(neuerToken).status
        LOG.info('Antwort: Login {}, neuer Token erhalten {}, geschützter Request damit {}',
                response.status, neuerToken as boolean, geschuetzt)

        then:
        response.status == 200
        neuerToken
        geschuetzt == 200
    }

    def "Token aus einer Registrierung ohne DeviceId wird mit 403 abgelehnt (DeviceId-Header #fall)"() {
        given:
        def registrierung = register('max', 'max@example.de', null)
        def body = json(registrierung)
        LOG.info('Szenario: registriert max ohne DeviceId -> {}, Token für Nutzer {}, DeviceId-Header {}',
                registrierung.status, body.id, headerDevice)

        expect:
        registrierung.status == 200
        callProtectedEndpoint(body.token, headerDevice).status == 403

        where:
        fall      | headerDevice
        'gesetzt' | DEVICE
        'fehlt'   | null
    }

    /** deviceId null: Body ohne deviceId (wie ein Client, der keine sendet). */
    private MockHttpServletResponse register(String name, String email, String deviceId = DEVICE) {
        def body = [name: name, email: email, password: 'geheim123']
        if (deviceId != null) {
            body.deviceId = deviceId
        }
        send(post('/api/auth/register'), body: body)
    }

    /** bearer: optional mitgeschickter (z. B. alter) Bearer-Token. */
    private MockHttpServletResponse authenticate(String email, String bearer = null) {
        def request = post('/api/auth/authenticate')
        if (bearer != null) {
            request.header('Authorization', JwtService.BEARER_PREFIX + bearer).header('deviceId', DEVICE)
        }
        send(request, body: [email: email, password: 'geheim123', deviceId: DEVICE])
    }

    /** deviceId null: Header wird weggelassen. */
    private MockHttpServletResponse callProtectedEndpoint(String jwt, String deviceId = DEVICE) {
        def request = get('/api/groups/sidenav').header('Authorization', JwtService.BEARER_PREFIX + jwt)
        if (deviceId != null) {
            request.header('deviceId', deviceId)
        }
        send(request)
    }

    private MockHttpServletResponse logout(String jwt) {
        send(post('/api/auth/logout')
                .header('Authorization', JwtService.BEARER_PREFIX + jwt)
                .header('deviceId', DEVICE))
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }
}
