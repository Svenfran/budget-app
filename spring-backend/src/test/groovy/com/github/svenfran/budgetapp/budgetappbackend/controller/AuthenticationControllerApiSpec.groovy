package com.github.svenfran.budgetapp.budgetappbackend.controller

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * DIV-22: E-Mail-Adressen sind unabhängig von Groß-/Kleinschreibung und umgebenden Leerzeichen.
 * DIV-18: JWT-Filter Ende-zu-Ende – widerrufene/abgelaufene Tokens (DB-Flags und JWT-exp), falsche DeviceId,
 * Logout, nicht verwertbare JWTs (403 statt Serverfehler) und öffentliche Endpunkte mit ungültigem Bearer-Header.
 * DIV-46: DeviceId ist Pflichtfeld bei Login und Registrierung (400, es wird kein Token gespeichert).
 * DIV-64: Login-Rate-Limit (5 Account+IP / 20 Account / 30 IP je 15 Minuten) – 429 mit Retry-After, Freigabe nach
 * Ablauf, keine Account Enumeration, Forwarding-Header ohne Wirkung (Test-Profil: Client-IP = remoteAddr), keine
 * Klartext-Kennungen im Log. Rollback-Spec: nach einer Abweisung werden nur Status/Antworten geprüft, keine Zähler.
 * Enthält auch Fälle des Fehlermeldungs-Vertrags (Liste der Meldungen: {@link CartControllerApiSpec}).
 */
class AuthenticationControllerApiSpec extends IntegrationSpec implements ApiRequests {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationControllerApiSpec)

    static final String DEVICE = 'device-1'
    /** Platzhalter für die Request-Helfer: deviceId wird im Body weggelassen (null sendet explizites JSON-null). */
    static final Object FEHLT = new Object()
    /** Dummy-Secret (Base64, 45 Byte), das nicht dem Test-Secret entspricht. */
    static final String FREMDER_SECRET = 'ZGl2dnktZnJlbWRlci1kdW1teS1zY2hsdWVzc2VsLW51ci1mdWVyLXRlc3Rz'
    static final Date ABGELAUFEN_AM = DateUtils.toDate(LocalDate.of(2025, 1, 1))
    static final Date AUSGESTELLT_AM = DateUtils.toDate(LocalDate.of(2024, 12, 11))
    /** Falsche Passwörter für Fehlversuche (DIV-64); FALSCH_LOG ist eindeutig genug für die Suche im Log. */
    static final String FALSCH = 'falsch'
    static final String FALSCH_LOG = 'falsches-passwort-div64'

    @Autowired UserRepository userRepository
    @Autowired TokenRepository tokenRepository
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

    def "Registrierung ohne DeviceId (#fall) wird mit 400 abgelehnt, es entstehen weder Nutzer noch Token"() {
        given:
        LOG.info('Szenario: neuer Nutzer max mit max@example.de, DeviceId {}', fall)

        when:
        def response = register('max', 'max@example.de', deviceId)
        def meldung = response.contentAsString
        def nutzerAngelegt = userRepository.findByEmail('max@example.de').isPresent()
        LOG.info('Antwort: {}, Nutzer angelegt {}', meldung, nutzerAngelegt)

        then:
        response.status == 400
        // Frontend erkennt den Fall am exakten Text (Fehlermeldungs-Vertrag X3)
        meldung == 'DeviceId is required'
        !nutzerAngelegt

        where:
        fall              | deviceId
        'fehlt'           | FEHLT
        'null'            | null
        'leer'            | ''
        'nur Leerzeichen' | '   '
    }

    def "Login ohne DeviceId (#fall) wird mit 400 abgelehnt, bestehende Tokens bleiben unverändert"() {
        given:
        def max = json(register('max', 'max@example.de'))
        def maxId = max.id as Long
        def tokenIdsVorher = tokenRepository.findAllByUserId(maxId)*.id
        LOG.info('Szenario: registriert max (Nutzer {}) auf {} mit Token {}, Login mit DeviceId {}',
                maxId, DEVICE, tokenIdsVorher, fall)

        when:
        def response = authenticate('max@example.de', null, deviceId)
        def meldung = response.contentAsString
        flushAndClear()
        def tokens = tokenRepository.findAllByUserId(maxId)
        def ungueltig = tokens.findAll { it.expired || it.revoked }*.id
        def geschuetzt = callProtectedEndpoint(max.token).status
        LOG.info('Antwort: {}, Tokens danach {}, davon expired/revoked {}, geschützter Request mit altem Token {}',
                meldung, tokens*.id, ungueltig, geschuetzt)

        then:
        response.status == 400
        meldung == 'DeviceId is required'
        tokens*.id == tokenIdsVorher
        ungueltig == []
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

    def "Kein Log (aller Logger, Security auf DEBUG) enthält Passwort, JWT, E-Mail oder IP im Klartext (AC7)"() {
        given:
        register('max', 'max@example.de')
        def ip = '198.51.100.77'
        def appender = new ListAppender<ILoggingEvent>()
        def root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as LogbackLogger
        def security = LoggerFactory.getLogger('org.springframework.security') as LogbackLogger
        def securityLevel = security.level
        LOG.info('Szenario: registriert max; Fehlversuch max, Fehlversuch unbekannt, gültiger Login, 6 Fehlversuche max (6. -> 429)')

        when:
        String jwt
        List<Integer> statusse
        appender.start()
        root.addAppender(appender)
        security.level = Level.DEBUG
        try {
            statusse = [authenticate('max@example.de', password: FALSCH_LOG, ip: ip).status,
                        authenticate('niemand@example.de', password: FALSCH_LOG, ip: ip).status]
            def erfolg = authenticate('max@example.de', ip: ip)
            jwt = json(erfolg).token
            statusse << erfolg.status
            statusse.addAll((1..6).collect { authenticate('max@example.de', password: FALSCH_LOG, ip: ip).status })
        } finally {
            security.level = securityLevel
            root.detachAppender(appender)
        }
        def zeilen = appender.list.collect { [it.loggerName, it.formattedMessage, it.throwableProxy?.message].join(' ') }
        def securityDebug = appender.list.count { it.loggerName.startsWith('org.springframework.security') && it.level == Level.DEBUG }
        def geheimnisse = ['geheim123', FALSCH_LOG, jwt, 'max@example.de', 'niemand@example.de', ip]
        def treffer = zeilen.findAll { zeile -> geheimnisse.any { zeile.contains(it) } }
        def ereignisse = appender.list*.formattedMessage.findAll { it.startsWith('event=') }
        LOG.info('Antwort: {}; {} Log-Zeilen ({} Security-DEBUG), Treffer {}, Ereignisse {}',
                statusse, zeilen.size(), securityDebug, treffer.size(), ereignisse)

        then:
        statusse == [403, 403, 200, 403, 403, 403, 403, 403, 429]
        securityDebug > 0
        treffer == []
        ereignisse.count { it ==~ /event=login_failed account=[0-9a-f]{16} ip=[0-9a-f]{16} ipSource=remoteAddr/ } == 7
        ereignisse.any { it ==~ /event=login_rate_limited scope=ACCOUNT_IP account=[0-9a-f]{16} ip=[0-9a-f]{16} ipSource=remoteAddr retryAfter=900/ }
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

    /** Sechs Fehlversuche; je Antwort Status, Body, Header-Namen und Retry-After (Vergleich in AC5). */
    private List<Map> verlauf(String email, String ip) {
        (1..6).collect {
            def response = authenticate(email, password: FALSCH, ip: ip)
            [status: response.status, body: response.contentAsString, header: response.headerNames.sort(),
             retryAfter: response.getHeader('Retry-After')]
        }
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
