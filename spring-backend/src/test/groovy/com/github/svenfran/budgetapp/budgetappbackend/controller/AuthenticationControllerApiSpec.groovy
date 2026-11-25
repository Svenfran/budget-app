package com.github.svenfran.budgetapp.budgetappbackend.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.AuthSessionService
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.ApiRequests
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.test.web.servlet.request.RequestPostProcessor

import java.time.Duration
import java.time.LocalDate

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * DIV-22: E-Mail-Adressen sind unabhängig von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * DIV-18/DIV-65: Access-Token-Prüfung Ende-zu-Ende mit echten Bearer-Headern (nicht send(user:)) – Session aus dem
 * Claim sid widerrufen/abgelaufen, falsche DeviceId, Legacy-JWT ohne sid, nicht verwertbare JWTs: 401 statt
 * Serverfehler; öffentliche Endpunkte mit ungültigem Bearer-Header.
 * DIV-46: DeviceId ist Pflichtfeld bei Login, Registrierung und Refresh (400, es entsteht keine Session).
 * DIV-64: Login-Rate-Limit (5 Account+IP / 20 Account / 30 IP je 15 Minuten) – 429 mit Retry-After, Freigabe nach
 * Ablauf, keine Account Enumeration, Forwarding-Header ohne Wirkung (Test-Profil: Client-IP = remoteAddr), keine
 * Klartext-Kennungen im Log. Rollback-Spec: nach einer Abweisung werden nur Status/Antworten geprüft, keine Zähler.
 * DIV-65: Login liefert Access (15 min, sid) + Refresh Token; Refresh mit Rotation, Kulanzfenster (60 s, ohne
 * Widerruf), Replay (Widerruf der Session), Leerlauf- und absolutes Ende; Logout per Refresh Token und/oder (auch
 * abgelaufenem) Bearer; nicht angemeldet → 401 UNAUTHENTICATED, Login-Fehler bleiben 403 mit leerem Body; Health nur
 * mit gültigem Session-Token. Zeit über testClock. Rollback-Spec: Service und Requests teilen die Test-Transaktion,
 * Widerrufe sind ohne Commit sichtbar (Commit-Verhalten: AuthSessionServiceCommitIntegrationSpec).
 * Enthält auch Fälle des Fehlercode-Vertrags mit Legacy-Text-Pins (Liste: {@link CartControllerApiSpec}).
 */
class AuthenticationControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationControllerApiSpec)

    static final String DEVICE = 'device-1'
    static final String DEVICE_2 = 'device-2'
    /** Platzhalter für die Request-Helfer: deviceId wird im Body weggelassen (null sendet explizites JSON-null). */
    static final Object FEHLT = new Object()
    /** Dummy-Secret (Base64, 45 Byte), das nicht dem Test-Secret entspricht. */
    static final String FREMDER_SECRET = 'ZGl2dnktZnJlbWRlci1kdW1teS1zY2hsdWVzc2VsLW51ci1mdWVyLXRlc3Rz'
    static final Date ABGELAUFEN_AM = DateUtils.toDate(LocalDate.of(2025, 1, 1))
    static final Date AUSGESTELLT_AM = DateUtils.toDate(LocalDate.of(2024, 12, 11))
    /** Falsche Passwörter für Fehlversuche (DIV-64); FALSCH_LOG ist eindeutig genug für die Suche im Log. */
    static final String FALSCH = 'falsch'
    static final String FALSCH_LOG = 'falsches-passwort-div64'
    /** Syntaktisch gültiger, aber nie ausgestellter Refresh Token (43 Zeichen Base64url). */
    static final String UNBEKANNTER_REFRESH_TOKEN = 'A' * 43

    @Autowired UserRepository userRepository
    @Autowired ObjectMapper objectMapper
    @Autowired JwtService jwtService
    @Autowired AuthSessionService authSessionService
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

    def "Registrierung speichert den Nutzernamen ohne umgebende Leerzeichen"() {
        given:
        LOG.info('Szenario: neuer Nutzer \' max \' mit max@example.de')

        when:
        def response = register(' max ', 'max@example.de')
        flushAndClear()
        LOG.info('Antwort: name \'{}\'', json(response).name)

        then:
        response.status == 200
        json(response).name == 'max'
        userRepository.findByEmail('max@example.de').get().name == 'max'
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

    def "Token mit abweichender Schreibweise der E-Mail im Subject bleibt gültig"() {
        given: 'Token wurde ausgestellt, als die Adresse noch als Alfred@Divvy.test gespeichert war'
        def alfred = fixtures.user(email: 'alfred@divvy.test')
        def sessionId = authSessionService.createSession(alfred, DEVICE).sessionId()
        def jwt = jwtService.generateToken(userDetails('Alfred@Divvy.test'), DEVICE, sessionId)
        LOG.info('Szenario: Session {} von Nutzer {} (alfred@divvy.test) auf {}, Token für Subject Alfred@Divvy.test',
                sessionId, alfred.id, DEVICE)

        expect:
        jwtService.extractUsername(jwt) == 'Alfred@Divvy.test'
        callProtectedEndpoint(jwt).status == 200
    }

    def "Token eines nicht (mehr) existierenden Nutzers führt zu 401 statt Serverfehler"() {
        given:
        def anderer = fixtures.user()
        def sessionId = authSessionService.createSession(anderer, DEVICE).sessionId()
        def jwt = jwtService.generateToken(userDetails('geloescht@divvy.test'), DEVICE, sessionId)
        LOG.info('Szenario: Token für Subject geloescht@divvy.test (kein Nutzer) mit Session {} von Nutzer {} auf {}',
                sessionId, anderer.id, DEVICE)

        expect:
        callProtectedEndpoint(jwt).status == 401
    }

    def "Token einer widerrufenen Session (#grund) wird mit 401 abgelehnt"() {
        given:
        def alfred = fixtures.user()
        def issued = authSessionService.createSession(alfred, DEVICE)
        def vorher = callProtectedEndpoint(issued.accessToken()).status
        authSessionService.revokeBySessionId(issued.sessionId(), grund)
        LOG.info('Szenario: Session {} von Nutzer {} auf {}, vor dem Widerruf {}, widerrufen mit {}',
                issued.sessionId(), alfred.id, DEVICE, vorher, grund)

        expect:
        vorher == 200
        callProtectedEndpoint(issued.accessToken()).status == 401

        where:
        grund << [SessionRevokeReason.LOGOUT, SessionRevokeReason.REUSE_DETECTED]
    }

    def "Falsche DeviceId (#fall) führt zu 401"() {
        given:
        def alfred = fixtures.user()
        def sessionId = authSessionService.createSession(alfred, sessionDevice).sessionId()
        def jwt = jwtService.generateToken(alfred, claimDevice, sessionId)
        LOG.info('Szenario: Session {} von Nutzer {} auf {}, DeviceId im Claim {}, im Header {}',
                sessionId, alfred.id, sessionDevice, claimDevice, headerDevice)

        expect:
        callProtectedEndpoint(jwt, headerDevice).status == 401

        where:
        fall                                 | claimDevice | sessionDevice | headerDevice
        'Header weicht ab'                   | DEVICE      | DEVICE        | DEVICE_2
        'Header fehlt'                       | DEVICE      | DEVICE        | null
        'DeviceId der Session weicht ab'     | DEVICE      | DEVICE_2      | DEVICE
        'Claim weicht ab'                    | DEVICE_2    | DEVICE        | DEVICE
    }

    def "Mit dem beim Login ausgegebenen Token ist ein geschützter Request erlaubt"() {
        given:
        register('max', 'max@example.de')
        def login = json(authenticate('max@example.de'))
        LOG.info('Szenario: registriert max mit max@example.de, Login auf {} liefert Token für Nutzer {}', DEVICE, login.id)

        expect:
        callProtectedEndpoint(login.token).status == 200
    }

    def "Nach dem Logout wird derselbe Token mit 401 abgelehnt"() {
        given:
        def registrierung = json(register('max', 'max@example.de'))
        def jwt = registrierung.token
        LOG.info('Szenario: registriert max mit max@example.de, Token für Nutzer {} auf {}', registrierung.id, DEVICE)

        when:
        def vorher = callProtectedEndpoint(jwt).status
        def abgemeldet = logout(bearer: jwt).status
        def nachher = callProtectedEndpoint(jwt).status
        LOG.info('Antwort: vor dem Logout {}, Logout {}, nach dem Logout {}', vorher, abgemeldet, nachher)

        then:
        vorher == 200
        abgemeldet == 200
        nachher == 401
    }

    def "Nicht verwertbares JWT (#fall) führt zu 401 statt Serverfehler"() {
        given:
        def alfred = fixtures.user()
        def sessionId = authSessionService.createSession(alfred, DEVICE).sessionId()
        def jwt = [
                'abgelaufen'    : { TestData.jwt(secret: secretKey, subject: alfred.email, deviceId: DEVICE, sessionId: sessionId,
                                                 issuedAt: AUSGESTELLT_AM, expiration: ABGELAUFEN_AM) },
                'fremd signiert': { TestData.jwt(secret: FREMDER_SECRET, subject: alfred.email, deviceId: DEVICE,
                                                 sessionId: sessionId, expiration: DateUtils.toDate(DateUtils.NO_END_DATE)) },
                'unlesbar'      : { 'kein-jwt' },
                'leer'          : { '' },
        ][fall]()
        LOG.info('Szenario: JWT {} für Nutzer {} mit aktiver Session {} auf {}', fall, alfred.id, sessionId, DEVICE)

        expect:
        callProtectedEndpoint(jwt).status == 401

        where:
        fall << ['abgelaufen', 'fremd signiert', 'unlesbar', 'leer']
    }

    def "JWT ohne sid (vor DIV-65 ausgestellt) wird mit 401 abgelehnt, auch bei gültiger Signatur und aktiver Session"() {
        given:
        def alfred = fixtures.user()
        def sessionId = authSessionService.createSession(alfred, DEVICE).sessionId()
        def legacy = TestData.jwt(secret: secretKey, subject: alfred.email, deviceId: DEVICE,
                expiration: DateUtils.toDate(DateUtils.NO_END_DATE))
        LOG.info('Szenario: Nutzer {} mit aktiver Session {} auf {}, Legacy-JWT ohne sid (exp 2999)', alfred.id, sessionId, DEVICE)

        expect:
        callProtectedEndpoint(legacy).status == 401
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

    def "Registrierung ohne DeviceId (#fall) wird mit 400 abgelehnt, es entstehen weder Nutzer noch Session"() {
        given:
        LOG.info('Szenario: neuer Nutzer max mit max@example.de, DeviceId {}', fall)

        when:
        def response = register('max', 'max@example.de', deviceId)
        def meldung = response.contentAsString
        def nutzerAngelegt = userRepository.findByEmail('max@example.de').isPresent()
        LOG.info('Antwort: {}, Nutzer angelegt {}', meldung, nutzerAngelegt)

        then:
        response.status == 400
        // Legacy-Text: ältere App-Versionen erkennen den Fall am exakten Text (Vertrag X3; neue per Code DEVICE_ID_REQUIRED)
        meldung == 'DeviceId is required'
        !nutzerAngelegt

        where:
        fall              | deviceId
        'fehlt'           | FEHLT
        'null'            | null
        'leer'            | ''
        'nur Leerzeichen' | '   '
    }

    def "Login ohne DeviceId (#fall) wird mit 400 abgelehnt, bestehende Sessions bleiben unverändert"() {
        given:
        def max = json(register('max', 'max@example.de'))
        def maxId = max.id as Long
        def sessionsVorher = aktiveSessions(maxId)
        LOG.info('Szenario: registriert max (Nutzer {}) auf {} mit aktiver Session {}, Login mit DeviceId {}',
                maxId, DEVICE, sessionsVorher, fall)

        when:
        def response = authenticate('max@example.de', null, deviceId)
        def meldung = response.contentAsString
        def sessions = aktiveSessions(maxId)
        def geschuetzt = callProtectedEndpoint(max.token).status
        LOG.info('Antwort: {}, aktive Sessions danach {}, geschützter Request mit altem Token {}', meldung, sessions, geschuetzt)

        then:
        response.status == 400
        meldung == 'DeviceId is required'
        sessions == sessionsVorher
        geschuetzt == 200

        where:
        fall              | deviceId
        'fehlt'           | FEHLT
        'null'            | null
        'leer'            | ''
        'nur Leerzeichen' | '   '
    }

    def "Ungültige E-Mail hat Vorrang vor fehlender DeviceId"() {
        given:
        LOG.info('Szenario: Registrierung max mit \'keine-email\' ohne DeviceId')

        when:
        def response = register('max', 'keine-email', FEHLT)
        LOG.info('Antwort: {}', response.contentAsString)

        then:
        response.status == 400
        response.contentAsString == 'Invalid Email'
    }

    // Login-Rate-Limit (DIV-64)

    def "Gültiger Login funktioniert nach 4 Fehlversuchen, danach zählen Fehlversuche wieder ab 0 (AC1)"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max, 4 Fehlversuche, gültiger Login, dann 6 Fehlversuche (alle von 127.0.0.1)')

        when:
        def vorher = (1..4).collect { authenticate('max@example.de', password: FALSCH).status }
        def gueltig = authenticate('max@example.de').status
        def danach = (1..6).collect { authenticate('max@example.de', password: FALSCH).status }
        LOG.info('Antwort: vorher {}, gültiger Login {}, danach {}', vorher, gueltig, danach)

        then:
        vorher == [403] * 4
        gueltig == 200
        danach == [403] * 5 + [429]
    }

    def "Nach 5 Fehlversuchen wird der 6. Versuch auch mit richtigem Passwort mit 429 und Retry-After abgewiesen (AC2)"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max, 5 Fehlversuche, dann Login mit richtigem Passwort, testClock {}', testClock.instant())

        when:
        def fehlversuche = (1..5).collect { authenticate('max@example.de', password: FALSCH) }
        def response = authenticate('max@example.de')
        LOG.info('Antwort: Fehlversuche {} (Bodies {}), 6. Versuch {} "{}" Retry-After {}', fehlversuche*.status,
                fehlversuche*.contentAsString, response.status, response.contentAsString, response.getHeader('Retry-After'))

        then:
        fehlversuche*.status == [403] * 5
        fehlversuche*.contentAsString == [''] * 5
        response.status == 429
        response.contentAsString == 'Too many login attempts'
        response.getHeader('Retry-After') == '900'
    }

    def "Nach Ablauf der Wartezeit ist der Login ohne Entsperrung wieder möglich (AC3)"() {
        given:
        register('max', 'max@example.de')
        5.times { authenticate('max@example.de', password: FALSCH) }
        def gesperrt = authenticate('max@example.de').status
        LOG.info('Szenario: registriert max, 5 Fehlversuche, 6. Versuch -> {}; Uhr +15 min', gesperrt)

        when:
        testClock.advance(Duration.ofMinutes(15))
        def response = authenticate('max@example.de')
        LOG.info('Antwort: Login nach 15 min -> {}', response.status)

        then:
        gesperrt == 429
        response.status == 200
        json(response).email == 'max@example.de'
    }

    def "Verteilte Fehlversuche auf einen Account greifen beim Account-Limit, andere Accounts bleiben unberührt (AC4)"() {
        given:
        register('max', 'max@example.de')
        register('moritz', 'moritz@example.de')
        def ips = (1..5).collect { "198.51.100.${it}".toString() }
        LOG.info('Szenario: registriert max und moritz, je 5 Fehlversuche auf max von {}', ips.take(4))

        when:
        def fehlversuche = ips.take(4).collectMany { ip -> (1..5).collect { authenticate('max@example.de', password: FALSCH, ip: ip).status } }
        def einundzwanzigster = authenticate('max@example.de', ip: ips[4])
        def moritz = authenticate('moritz@example.de', ip: ips[0])
        LOG.info('Antwort: 20 Fehlversuche {}, 21. Versuch von {} -> {} (Retry-After {}), moritz von {} -> {}',
                fehlversuche.countBy { it }, ips[4], einundzwanzigster.status, einundzwanzigster.getHeader('Retry-After'),
                ips[0], moritz.status)

        then:
        fehlversuche == [403] * 20
        einundzwanzigster.status == 429
        moritz.status == 200
    }

    def "Fehlversuche auf viele Accounts von einer IP greifen beim IP-Limit (AC4)"() {
        given:
        def ip = '198.51.100.20'
        LOG.info('Szenario: 31 verschiedene unbekannte Accounts von {}', ip)

        when:
        def statusse = (1..31).collect { authenticate("spray-${it}@example.org".toString(), password: FALSCH, ip: ip).status }
        LOG.info('Antwort: {}', statusse.countBy { it })

        then:
        statusse == [403] * 30 + [429]
    }

    def "Existierende und unbekannte E-Mail sind in Status, Body, Headern und Rate-Limit nicht unterscheidbar (AC5)"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max; je 6 Fehlversuche auf max (von .31) und auf eine unbekannte E-Mail (von .32)')

        when:
        def existierend = verlauf('max@example.de', '198.51.100.31')
        def unbekannt = verlauf('niemand@example.de', '198.51.100.32')
        LOG.info('Antwort: existierend {}; unbekannt {}', existierend, unbekannt)

        then:
        existierend*.status == [403] * 5 + [429]
        existierend == unbekannt
    }

    def "Fehlversuche mit abweichender Schreibweise zählen auf denselben Account"() {
        given:
        register('max', 'max@example.de')
        def schreibweisen = ['max@example.de', 'MAX@Example.de', ' max@example.de ', 'Max@EXAMPLE.de', 'max@example.DE', 'mAx@example.de']
        LOG.info('Szenario: registriert max, 6 Fehlversuche in verschiedenen Schreibweisen')

        when:
        def statusse = schreibweisen.collect { authenticate(it, password: FALSCH).status }
        LOG.info('Antwort: {}', statusse)

        then:
        statusse == [403] * 5 + [429]
    }

    def "Wechselnde X-Forwarded-For- und X-Real-IP-Header umgehen das IP-Limit nicht (AC7)"() {
        given:
        LOG.info('Szenario: 31 unbekannte Accounts von 127.0.0.1, jeder Request mit anderem X-Forwarded-For und X-Real-IP')

        when:
        def statusse = (1..31).collect {
            authenticate("spoof-${it}@example.org".toString(), password: FALSCH,
                    headers: ['X-Forwarded-For': "10.0.0.${it}", 'X-Real-IP': "10.1.0.${it}"]).status
        }
        LOG.info('Antwort: {}', statusse.countBy { it })

        then:
        statusse == [403] * 30 + [429]
    }

    def "Ungültige E-Mail und fehlende DeviceId zählen nicht als Versuch"() {
        given:
        register('max', 'max@example.de')
        LOG.info('Szenario: registriert max, 31 Logins mit ungültiger E-Mail und 10 ohne DeviceId für max, dann gültiger Login')

        when:
        def ungueltig = (1..31).collect { authenticate("keine-email-${it}".toString()).contentAsString }
        def ohneDevice = (1..10).collect { authenticate('max@example.de', null, FEHLT).contentAsString }
        def gueltig = authenticate('max@example.de').status
        LOG.info('Antwort: {} / {}, gültiger Login {}', ungueltig.countBy { it }, ohneDevice.countBy { it }, gueltig)

        then:
        ungueltig == ['Invalid Email'] * 31
        ohneDevice == ['DeviceId is required'] * 10
        gueltig == 200
    }

    def "429 aus einer anderen Origin: Retry-After ist per CORS für die App lesbar"() {
        given: 'Origin aus app.cors.allowed-origins (Basiswert) – die App läuft als WebView auf einer anderen Origin als das Backend'
        def origin = 'http://localhost:8100'
        register('max', 'max@example.de')
        5.times { authenticate('max@example.de', password: FALSCH) }
        LOG.info('Szenario: registriert max, 5 Fehlversuche, 6. Versuch mit Origin {}', origin)

        when:
        def response = authenticate('max@example.de', headers: [Origin: origin])
        def erlaubteOrigin = response.getHeader('Access-Control-Allow-Origin')
        def lesbar = response.getHeader('Access-Control-Expose-Headers')
        LOG.info('Antwort: {}, Retry-After {}, Allow-Origin {}, Expose-Headers {}',
                response.status, response.getHeader('Retry-After'), erlaubteOrigin, lesbar)

        then:
        response.status == 429
        response.getHeader('Retry-After') == '900'
        erlaubteOrigin == origin
        lesbar.split(',')*.trim().contains('Retry-After')
    }

    def "429 mit X-Error-Format json: Code TOO_MANY_LOGIN_ATTEMPTS, bisheriger Text und Retry-After (R1)"() {
        given:
        register('max', 'max@example.de')
        5.times { authenticate('max@example.de', password: FALSCH) }
        LOG.info('Szenario: registriert max, 5 Fehlversuche, 6. Versuch mit X-Error-Format json')

        when:
        def response = authenticate('max@example.de', headers: ['X-Error-Format': 'json'])
        def antwort = json(response)
        LOG.info('Antwort: {} {} ({}), Retry-After {}', response.status, antwort, response.contentType, response.getHeader('Retry-After'))

        then:
        response.status == 429
        response.contentType == 'application/json'
        antwort.code == 'TOO_MANY_LOGIN_ATTEMPTS'
        antwort.message == 'Too many login attempts'
        !antwort.containsKey('details')
        response.getHeader('Retry-After') == '900'
    }

    def "Kein Log (aller Logger, Security auf DEBUG) enthält Passwort, Token, E-Mail oder IP im Klartext (AC7, DIV-65)"() {
        given:
        register('max', 'max@example.de')
        def ip = '198.51.100.77'
        def appender = new ListAppender<ILoggingEvent>()
        def root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as LogbackLogger
        def security = LoggerFactory.getLogger('org.springframework.security') as LogbackLogger
        def securityLevel = security.level
        LOG.info('Szenario: registriert max; Fehlversuch max, Fehlversuch unbekannt, gültiger Login, 6 Fehlversuche max '
                + '(6. -> 429); danach Refresh, Replay des alten Refresh Tokens nach 61 s und Logout')

        when:
        List<String> tokens = []
        List<Integer> statusse
        List<Integer> sessionStatusse
        appender.start()
        root.addAppender(appender)
        security.level = Level.DEBUG
        try {
            statusse = [authenticate('max@example.de', password: FALSCH_LOG, ip: ip).status,
                        authenticate('niemand@example.de', password: FALSCH_LOG, ip: ip).status]
            def erfolg = authenticate('max@example.de', ip: ip)
            def login = json(erfolg)
            tokens += [login.token, login.refreshToken]
            statusse << erfolg.status
            statusse.addAll((1..6).collect { authenticate('max@example.de', password: FALSCH_LOG, ip: ip).status })

            def erneuert = refresh(login.refreshToken)
            def paar = json(erneuert)
            tokens += [paar.token, paar.refreshToken]
            testClock.advance(Duration.ofSeconds(61))
            sessionStatusse = [erneuert.status, refresh(login.refreshToken).status,
                               logout(refreshToken: paar.refreshToken, bearer: paar.token).status]
        } finally {
            security.level = securityLevel
            root.detachAppender(appender)
        }
        def zeilen = appender.list.collect { [it.loggerName, it.formattedMessage, it.throwableProxy?.message].join(' ') }
        def securityDebug = appender.list.count { it.loggerName.startsWith('org.springframework.security') && it.level == Level.DEBUG }
        def geheimnisse = ['geheim123', FALSCH_LOG, 'max@example.de', 'niemand@example.de', ip] + tokens
        def treffer = zeilen.findAll { zeile -> geheimnisse.any { zeile.contains(it) } }
        def ereignisse = appender.list*.formattedMessage.findAll { it.startsWith('event=') }
        LOG.info('Antwort: {} / {}; {} Log-Zeilen ({} Security-DEBUG), Treffer {}, Ereignisse {}',
                statusse, sessionStatusse, zeilen.size(), securityDebug, treffer.size(), ereignisse)

        then:
        statusse == [403, 403, 200, 403, 403, 403, 403, 403, 429]
        sessionStatusse == [200, 401, 200]
        tokens.every { it }
        securityDebug > 0
        treffer == []
        ereignisse.count { it ==~ /event=login_failed account=[0-9a-f]{16} ip=[0-9a-f]{16} ipSource=remoteAddr/ } == 7
        ereignisse.any { it ==~ /event=login_rate_limited scope=ACCOUNT_IP account=[0-9a-f]{16} ip=[0-9a-f]{16} ipSource=remoteAddr retryAfter=900/ }
        ereignisse.any { it ==~ /event=session_created sessionId=\d+ userId=\d+/ }
        ereignisse.any { it ==~ /event=refresh_token_reuse_detected sessionId=\d+ userId=\d+/ }
    }

    def "Mit X-Error-Format json: #fall -> 400 mit Code #code und bisherigem Text"() {
        given: 'max ist mit max@example.de registriert'
        register('max', 'max@example.de')
        def body = (name == null ? [:] : [name: name]) + [email: email, password: 'geheim123'] + (deviceId.is(FEHLT) ? [:] : [deviceId: deviceId])
        LOG.info('Szenario: max@example.de (max) existiert; {} {} {}', endpoint, fall, body.findAll { it.key != 'password' })

        when:
        def response = send(post(endpoint).header('X-Error-Format', 'json'), body: body)
        def antwort = json(response)
        LOG.info('Antwort: {} {} ({})', response.status, antwort, response.contentType)

        then:
        response.status == 400
        response.contentType == 'application/json'
        antwort.code == code
        antwort.message == text
        !antwort.containsKey('details')

        where:
        fall                               | endpoint                 | name     | email               | deviceId || code                       | text
        'vergebene E-Mail (M5)'            | '/api/auth/register'     | 'moritz' | 'max@example.de'    | DEVICE   || 'EMAIL_ALREADY_EXISTS'     | 'User with email max@example.de already exists'
        'vergebener Name (M6)'             | '/api/auth/register'     | 'max'    | 'moritz@example.de' | DEVICE   || 'USER_NAME_ALREADY_EXISTS' | 'User with name max already exists'
        'Registrierung ohne DeviceId (X3)' | '/api/auth/register'     | 'moritz' | 'moritz@example.de' | FEHLT    || 'DEVICE_ID_REQUIRED'       | 'DeviceId is required'
        'Login ohne DeviceId (X3)'         | '/api/auth/authenticate' | null     | 'max@example.de'    | FEHLT    || 'DEVICE_ID_REQUIRED'       | 'DeviceId is required'
        'ungültige E-Mail (X1)'            | '/api/auth/register'     | 'moritz' | 'keine-email'       | DEVICE   || 'INVALID_EMAIL'            | 'Invalid Email'
    }

    def "CORS-Preflight der App erlaubt den Header X-Error-Format"() {
        given: 'WebView-Origin der Prod-App (androidScheme https); http://localhost wäre für MockMvc (localhost:80) same-origin'
        LOG.info('Szenario: Preflight von https://localhost für POST /api/auth/authenticate mit x-error-format')

        when:
        def response = send(options('/api/auth/authenticate')
                .header('Origin', 'https://localhost')
                .header('Access-Control-Request-Method', 'POST')
                .header('Access-Control-Request-Headers', 'content-type,x-error-format'))
        def erlaubt = response.getHeader('Access-Control-Allow-Headers')
        LOG.info('Antwort: {}, Access-Control-Allow-Headers {}', response.status, erlaubt)

        then:
        response.status == 200
        erlaubt?.toLowerCase(Locale.ROOT)?.contains('x-error-format')
    }

    // Refresh-Token-Sessions (DIV-65)

    def "Login liefert Access Token (15 min), Refresh Token und Session-Ablauf (90 Tage) (AC1)"() {
        given:
        def max = json(register('max', 'max@example.de'))
        def jetzt = testClock.instant()
        LOG.info('Szenario: registriert max (Nutzer {}), Login auf {} um {}', max.id, DEVICE, jetzt)

        when:
        def response = authenticate('max@example.de')
        def login = json(response)
        def sessionId = jwtService.extractSessionId(login.token)
        def aktiv = aktiveSessions(max.id as Long)
        LOG.info('Antwort: {}, Session {}, expirationDate {}, sessionExpirationDate {}, Refresh Token {} Zeichen; aktive Sessions {}',
                response.status, sessionId, login.expirationDate, login.sessionExpirationDate, login.refreshToken?.size(), aktiv)

        then:
        response.status == 200
        login.expirationDate == (jetzt + Duration.ofMinutes(15)).toEpochMilli()
        login.sessionExpirationDate == (jetzt + Duration.ofDays(90)).toEpochMilli()
        login.refreshToken ==~ /[A-Za-z0-9_-]{43}/
        jwtService.extractDeviceId(login.token) == DEVICE

        and: 'die Session der Registrierung auf demselben Gerät ist durch den Login ersetzt'
        aktiv == [sessionId]
    }

    def "Registrierung liefert ebenfalls ein Token-Paar mit aktiver Session"() {
        given:
        LOG.info('Szenario: neuer Nutzer max auf {}', DEVICE)

        when:
        def registrierung = json(register('max', 'max@example.de'))
        def sessionId = jwtService.extractSessionId(registrierung.token)
        def aktiv = aktiveSessions(registrierung.id as Long)
        def erneuert = refresh(registrierung.refreshToken).status
        LOG.info('Antwort: Nutzer {}, Session {}, aktive Sessions {}, Refresh mit dem Refresh Token -> {}',
                registrierung.id, sessionId, aktiv, erneuert)

        then:
        aktiv == [sessionId]
        erneuert == 200
    }

    def "Access Token gilt 15 Minuten, danach 401 UNAUTHENTICATED"() {
        given:
        def login = loginMax()
        LOG.info('Szenario: max angemeldet auf {} um {}', DEVICE, testClock.instant())

        when:
        testClock.advance(Duration.ofMinutes(14))
        def nach14 = callProtectedEndpoint(login.token).status
        testClock.advance(Duration.ofMinutes(1).plusSeconds(1))
        def nach15 = callProtectedEndpoint(login.token, DEVICE, ['X-Error-Format': 'json'])
        def antwort = json(nach15)
        LOG.info('Antwort: nach 14 min {}, nach 15 min 1 s {} {}', nach14, nach15.status, antwort)

        then:
        nach14 == 200
        nach15.status == 401
        antwort.code == 'UNAUTHENTICATED'
    }

    def "Refresh liefert ein neues Paar derselben Session, der neue Access Token funktioniert (AC2)"() {
        given:
        def login = loginMax()
        def sessionId = jwtService.extractSessionId(login.token)
        testClock.advance(Duration.ofMinutes(16))
        def jetzt = testClock.instant()
        LOG.info('Szenario: max angemeldet auf {} (Session {}), 16 min später, Access Token abgelaufen', DEVICE, sessionId)

        when:
        def alt = callProtectedEndpoint(login.token).status
        def response = refresh(login.refreshToken)
        def paar = json(response)
        def neu = callProtectedEndpoint(paar.token).status
        LOG.info('Antwort: alter Access Token {}, Refresh {}, neuer Access Token {}, expirationDate {}', alt, response.status,
                neu, paar.expirationDate)

        then:
        alt == 401
        response.status == 200
        neu == 200
        jwtService.extractSessionId(paar.token) == sessionId
        paar.refreshToken != login.refreshToken
        paar.expirationDate == (jetzt + Duration.ofMinutes(15)).toEpochMilli()
        paar.sessionExpirationDate == login.sessionExpirationDate
        paar.id == login.id
        paar.email == 'max@example.de'
    }

    def "Alter Refresh Token im Kulanzfenster: 401 REFRESH_TOKEN_INVALID ohne Widerruf der Session (E5)"() {
        given:
        def login = loginMax()
        def paar = json(refresh(login.refreshToken))
        testClock.advance(Duration.ofSeconds(30))
        LOG.info('Szenario: max angemeldet, einmal rotiert; 30 s später erneut mit dem alten Refresh Token')

        when:
        def duplikat = refresh(login.refreshToken, DEVICE, ['X-Error-Format': 'json'])
        def antwort = json(duplikat)
        def neuerAccess = callProtectedEndpoint(paar.token).status
        def weiter = refresh(paar.refreshToken).status
        LOG.info('Antwort: Duplikat {} {}, neuer Access Token danach {}, Refresh mit dem neuen Refresh Token {}',
                duplikat.status, antwort, neuerAccess, weiter)

        then:
        duplikat.status == 401
        antwort.code == 'REFRESH_TOKEN_INVALID'
        neuerAccess == 200
        weiter == 200
    }

    def "Alter Refresh Token nach dem Kulanzfenster: Replay widerruft die Session samt neuem Access Token (AC3)"() {
        given:
        def login = loginMax()
        def sessionId = jwtService.extractSessionId(login.token)
        def paar = json(refresh(login.refreshToken))
        testClock.advance(Duration.ofSeconds(61))
        LOG.info('Szenario: max angemeldet (Session {}), einmal rotiert; 61 s später erneut mit dem alten Refresh Token', sessionId)

        when:
        def replay = refresh(login.refreshToken).status
        def neuerAccess = callProtectedEndpoint(paar.token).status
        def neuerRefresh = refresh(paar.refreshToken).status
        def grund = widerrufsgrund(sessionId)
        LOG.info('Antwort: Replay {}, neuer Access Token {}, neuer Refresh Token {}, Session widerrufen mit {}',
                replay, neuerAccess, neuerRefresh, grund)

        then:
        replay == 401
        neuerAccess == 401
        neuerRefresh == 401
        grund == SessionRevokeReason.REUSE_DETECTED
    }

    def "Refresh nach 30 Tagen ohne Nutzung: 401"() {
        given:
        def login = loginMax()
        testClock.advance(Duration.ofDays(30))
        LOG.info('Szenario: max angemeldet, 30 Tage keine Nutzung')

        expect:
        refresh(login.refreshToken).status == 401
    }

    def "Session endet nach 90 Tagen trotz regelmäßiger Rotation (AC8)"() {
        given:
        def login = loginMax()
        LOG.info('Szenario: max angemeldet; Refresh an Tag 20, 40, 60, 80, dann an Tag 90')

        when:
        def refreshToken = login.refreshToken
        def statusse = []
        def sessionEnden = []
        4.times {
            testClock.advance(Duration.ofDays(20))
            def response = refresh(refreshToken)
            def paar = json(response)
            statusse << response.status
            sessionEnden << paar.sessionExpirationDate
            refreshToken = paar.refreshToken
        }
        testClock.advance(Duration.ofDays(10))
        def tag90 = refresh(refreshToken).status
        LOG.info('Antwort: Tag 20 bis 80 {}, Session-Ende jeweils {}, Tag 90 {}', statusse, sessionEnden.unique(false), tag90)

        then:
        statusse == [200] * 4
        sessionEnden.every { it == login.sessionExpirationDate }
        tag90 == 401
    }

    def "Refresh mit #fall: #status, die Session bleibt nutzbar"() {
        given:
        def login = loginMax()
        LOG.info('Szenario: max angemeldet auf {}, Refresh mit {}', DEVICE, fall)

        when:
        def response = refresh(login.refreshToken, deviceId)
        def meldung = response.contentAsString
        def danach = refresh(login.refreshToken).status
        LOG.info('Antwort: {} "{}", danach Refresh mit richtiger DeviceId {}', response.status, meldung, danach)

        then:
        response.status == status
        meldung == text
        danach == 200

        where:
        fall                 | deviceId || status | text
        'falscher DeviceId'  | DEVICE_2 || 401    | 'Invalid refresh token'
        'fehlender DeviceId' | FEHLT    || 400    | 'DeviceId is required'
        'leerer DeviceId'    | '  '     || 400    | 'DeviceId is required'
    }

    def "Refresh mit #fall Refresh Token: 401"() {
        given:
        LOG.info('Szenario: Refresh mit {} Refresh Token', fall)

        expect:
        refresh(token).status == 401

        where:
        fall          | token
        'unbekanntem' | UNBEKANNTER_REFRESH_TOKEN
        'leerem'      | ''
        'fehlendem'   | null
    }

    def "Refresh Token als Bearer-Token verwendet: 401 (FA8)"() {
        given:
        def login = loginMax()
        LOG.info('Szenario: max angemeldet, geschützter Request mit dem Refresh Token als Bearer')

        expect:
        callProtectedEndpoint(login.refreshToken).status == 401
    }

    def "Login auf zweitem Gerät lässt die erste Session aktiv, erneuter Login auf demselben Gerät ersetzt sie"() {
        given:
        def erstes = loginMax()
        def zweites = json(authenticate('max@example.de', null, DEVICE_2))
        LOG.info('Szenario: max angemeldet auf {} (Session {}) und {} (Session {}), dann erneut auf {}', DEVICE,
                jwtService.extractSessionId(erstes.token), DEVICE_2, jwtService.extractSessionId(zweites.token), DEVICE)

        when:
        def beideAktiv = [callProtectedEndpoint(erstes.token).status, callProtectedEndpoint(zweites.token, DEVICE_2).status]
        def neu = json(authenticate('max@example.de'))
        def danach = [alt       : callProtectedEndpoint(erstes.token).status,
                      altRefresh: refresh(erstes.refreshToken).status,
                      zweites   : callProtectedEndpoint(zweites.token, DEVICE_2).status,
                      neu       : callProtectedEndpoint(neu.token).status]
        def grund = widerrufsgrund(jwtService.extractSessionId(erstes.token))
        LOG.info('Antwort: vorher {}, nach erneutem Login {}, alte Session widerrufen mit {}', beideAktiv, danach, grund)

        then:
        beideAktiv == [200, 200]
        danach == [alt: 401, altRefresh: 401, zweites: 200, neu: 200]
        grund == SessionRevokeReason.RELOGIN
    }

    def "Logout #fall beendet die Session, das zweite Gerät bleibt angemeldet (AC7)"() {
        given:
        def erstes = loginMax()
        def zweites = json(authenticate('max@example.de', null, DEVICE_2))
        def sessionId = jwtService.extractSessionId(erstes.token)
        testClock.advance(Duration.ofMinutes(minutenSpaeter))
        LOG.info('Szenario: max angemeldet auf {} (Session {}) und {}; {} min später Logout auf {} {}', DEVICE, sessionId,
                DEVICE_2, minutenSpaeter, DEVICE, fall)

        when:
        def abgemeldet = logout(refreshToken: mitRefresh ? erstes.refreshToken : null,
                bearer: mitBearer ? erstes.token : null).status
        def access = callProtectedEndpoint(erstes.token).status
        def erneuern = refresh(erstes.refreshToken).status
        def grund = widerrufsgrund(sessionId)
        def zweitesGeraet = refresh(zweites.refreshToken, DEVICE_2).status
        LOG.info('Antwort: Logout {}, danach Access Token {}, Refresh {}, Widerruf {}, Refresh auf {} {}', abgemeldet, access,
                erneuern, grund, DEVICE_2, zweitesGeraet)

        then:
        abgemeldet == 200
        access == 401
        erneuern == 401
        grund == SessionRevokeReason.LOGOUT
        zweitesGeraet == 200

        where:
        fall                                              | mitRefresh | mitBearer | minutenSpaeter
        'mit Refresh Token'                               | true       | false     | 1
        'nur mit Bearer-Token'                            | false      | true      | 1
        'mit abgelaufenem Access Token und Refresh Token' | true       | true      | 16
        'nur mit abgelaufenem Bearer-Token'               | false      | true      | 16
    }

    def "Logout #fall: 200"() {
        given:
        def login = loginMax()
        LOG.info('Szenario: max angemeldet, Logout {}', fall)

        when:
        def statusse = (1..wiederholungen).collect {
            logout(refreshToken: refreshToken == 'eigener' ? login.refreshToken : refreshToken,
                    bearer: bearer == 'eigener' ? login.token : bearer).status
        }
        def access = callProtectedEndpoint(login.token).status
        LOG.info('Antwort: {}, eigener Access Token danach {}', statusse, access)

        then:
        statusse == [200] * wiederholungen
        access == accessDanach

        where:
        fall                            | refreshToken              | bearer     | wiederholungen || accessDanach
        'ohne Token'                    | null                      | null       | 1              || 200
        'mit unbekanntem Refresh Token' | UNBEKANNTER_REFRESH_TOKEN | null       | 1              || 200
        'mit unlesbarem Bearer-Token'   | null                      | 'kein-jwt' | 1              || 200
        'zweimal hintereinander'        | 'eigener'                 | 'eigener'  | 2              || 401
    }

    def "Logout ohne Body und ohne Content-Type: 200"() {
        given:
        LOG.info('Szenario: POST /api/auth/logout ohne Body (wie ältere App-Versionen)')

        expect:
        send(post('/api/auth/logout')).status == 200
    }

    def "Unbekannte E-Mail: 403 mit leerem Body, es entsteht keine Session"() {
        given:
        def vorher = sessionAnzahl()
        LOG.info('Szenario: Login mit niemand@example.de (kein Nutzer), {} Sessions vorhanden', vorher)

        when:
        def response = authenticate('niemand@example.de')
        def nachher = sessionAnzahl()
        LOG.info('Antwort: {} "{}", Sessions danach {}', response.status, response.contentAsString, nachher)

        then:
        response.status == 403
        response.contentAsString == ''
        nachher == vorher
    }

    def "Health (#fall): #status"() {
        given:
        def login = loginMax()
        if (widerrufen) {
            authSessionService.revokeBySessionId(jwtService.extractSessionId(login.token), SessionRevokeReason.LOGOUT)
        }
        LOG.info('Szenario: max angemeldet, Session widerrufen {}, GET /actuator/health {}', widerrufen, fall)

        when:
        def request = get('/actuator/health')
        if (mitToken) {
            request.header('Authorization', JwtService.BEARER_PREFIX + login.token).header('deviceId', DEVICE)
        }
        def response = send(request)
        LOG.info('Antwort: {}', response.status)

        then:
        response.status == status

        where:
        fall                         | mitToken | widerrufen || status
        'mit gültigem Session-Token' | true     | false      || 200
        'ohne Token'                 | false    | false      || 401
        'mit widerrufener Session'   | true     | true       || 401
    }

    def "#fall mit X-Error-Format #header: 401 mit Code bzw. Legacy-Text"() {
        given:
        LOG.info('Szenario: {} mit X-Error-Format {}', fall, header)

        when:
        def headers = header == null ? [:] : ['X-Error-Format': header]
        def response
        if (fall == 'nicht angemeldet') {
            def request = get('/api/groups/sidenav')
            headers.each { name, wert -> request.header(name, wert) }
            response = send(request)
        } else {
            response = refresh(UNBEKANNTER_REFRESH_TOKEN, DEVICE, headers)
        }
        def body = header == null ? response.contentAsString : json(response)
        LOG.info('Antwort: {} {} ({})', response.status, body, response.contentType)

        then:
        response.status == 401
        body == erwartet

        where:
        fall                  | header || erwartet
        'nicht angemeldet'    | 'json' || [code: 'UNAUTHENTICATED', message: 'Unauthenticated']
        'nicht angemeldet'    | null   || 'Unauthenticated'
        'abgelehnter Refresh' | 'json' || [code: 'REFRESH_TOKEN_INVALID', message: 'Invalid refresh token']
        'abgelehnter Refresh' | null   || 'Invalid refresh token'
    }

    def "CORS-Preflight der App für #endpoint ist erlaubt"() {
        given: 'WebView-Origin der Prod-App (androidScheme https)'
        LOG.info('Szenario: Preflight von https://localhost für POST {}', endpoint)

        when:
        def response = send(options(endpoint)
                .header('Origin', 'https://localhost')
                .header('Access-Control-Request-Method', 'POST')
                .header('Access-Control-Request-Headers', 'authorization,content-type,deviceid,x-error-format'))
        def origin = response.getHeader('Access-Control-Allow-Origin')
        LOG.info('Antwort: {}, Allow-Origin {}, Allow-Headers {}', response.status, origin,
                response.getHeader('Access-Control-Allow-Headers'))

        then:
        response.status == 200
        origin == 'https://localhost'

        where:
        endpoint << ['/api/auth/refresh', '/api/auth/logout']
    }
    /** deviceId FEHLT: Body ohne deviceId (wie ein Client, der keine sendet); null: explizites JSON-null. */
    private MockHttpServletResponse register(String name, String email, Object deviceId = DEVICE) {
        def body = [name: name, email: email, password: 'geheim123']
        if (!deviceId.is(FEHLT)) {
            body.deviceId = deviceId
        }
        send(post('/api/auth/register'), body: body)
    }

    /** bearer: optional mitgeschickter (z. B. alter) Bearer-Token; deviceId wie bei register. */
    private MockHttpServletResponse authenticate(String email, String bearer = null, Object deviceId = DEVICE) {
        def request = post('/api/auth/authenticate')
        if (bearer != null) {
            request.header('Authorization', JwtService.BEARER_PREFIX + bearer).header('deviceId', DEVICE)
        }
        def body = [email: email, password: 'geheim123']
        if (!deviceId.is(FEHLT)) {
            body.deviceId = deviceId
        }
        send(request, body: body)
    }

    /**
     * Login mit Optionen (DIV-64): password (Standard geheim123), ip (remoteAddr; ohne Angabe der MockMvc-Wert
     * 127.0.0.1), headers (zusätzliche Request-Header).
     */
    private MockHttpServletResponse authenticate(Map optionen, String email) {
        assert ['password', 'ip', 'headers'].containsAll(optionen.keySet()): "Unbekannte Option: ${optionen.keySet()}"
        def request = post('/api/auth/authenticate')
        if (optionen.ip) {
            def ip = optionen.ip as String
            request.with({ MockHttpServletRequest r -> r.remoteAddr = ip; r } as RequestPostProcessor)
        }
        (optionen.headers ?: [:]).each { name, wert -> request.header(name as String, wert as String) }
        send(request, body: [email: email, password: optionen.password ?: 'geheim123', deviceId: DEVICE])
    }

    /** Registriert max@example.de und meldet ihn auf DEVICE an; liefert die Login-Antwort (DIV-65). */
    private Map loginMax() {
        register('max', 'max@example.de')
        def response = authenticate('max@example.de')
        assert response.status == 200
        json(response) as Map
    }

    /** Sechs Fehlversuche; je Antwort Status, Body, Header-Namen und Retry-After (Vergleich in AC5). */
    private List<Map> verlauf(String email, String ip) {
        (1..6).collect {
            def response = authenticate(email, password: FALSCH, ip: ip)
            [status: response.status, body: response.contentAsString, header: response.headerNames.sort(),
             retryAfter: response.getHeader('Retry-After')]
        }
    }

    /** deviceId null: Header wird weggelassen; headers: zusätzliche Request-Header. */
    private MockHttpServletResponse callProtectedEndpoint(String jwt, String deviceId = DEVICE, Map headers = [:]) {
        def request = get('/api/groups/sidenav').header('Authorization', JwtService.BEARER_PREFIX + jwt)
        if (deviceId != null) {
            request.header('deviceId', deviceId)
        }
        headers.each { name, wert -> request.header(name as String, wert as String) }
        send(request)
    }

    /** POST /api/auth/refresh (DIV-65); deviceId FEHLT wie bei register, headers: zusätzliche Request-Header. */
    private MockHttpServletResponse refresh(String refreshToken, Object deviceId = DEVICE, Map headers = [:]) {
        def request = post('/api/auth/refresh')
        headers.each { name, wert -> request.header(name as String, wert as String) }
        def body = [refreshToken: refreshToken]
        if (!deviceId.is(FEHLT)) {
            body.deviceId = deviceId
        }
        send(request, body: body)
    }

    /** POST /api/auth/logout (DIV-65); refreshToken (Body) und bearer (Header) jeweils optional. */
    private MockHttpServletResponse logout(Map optionen) {
        assert ['refreshToken', 'bearer'].containsAll(optionen.keySet()): "Unbekannte Option: ${optionen.keySet()}"
        def request = post('/api/auth/logout')
        if (optionen.bearer != null) {
            request.header('Authorization', JwtService.BEARER_PREFIX + optionen.bearer).header('deviceId', DEVICE)
        }
        optionen.refreshToken == null ? send(request) : send(request, body: [refreshToken: optionen.refreshToken])
    }

    /** Ids der nicht widerrufenen Sessions des Nutzers (JPQL: Hibernate flusht vorher). */
    private List<Long> aktiveSessions(Long userId) {
        entityManager.createQuery(
                'select s.id from AuthSession s where s.user.id = :userId and s.revokedAt is null order by s.id', Long)
                .setParameter('userId', userId)
                .resultList
    }

    private SessionRevokeReason widerrufsgrund(Long sessionId) {
        entityManager.createQuery('select s.revokeReason from AuthSession s where s.id = :id', SessionRevokeReason)
                .setParameter('id', sessionId)
                .singleResult
    }

    /** Anzahl aller Sessions; nur als Vorher/Nachher-Vergleich innerhalb eines Features (Commit-Specs hinterlassen Zeilen). */
    private long sessionAnzahl() {
        entityManager.createQuery('select count(s) from AuthSession s', Long).singleResult
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }
}
