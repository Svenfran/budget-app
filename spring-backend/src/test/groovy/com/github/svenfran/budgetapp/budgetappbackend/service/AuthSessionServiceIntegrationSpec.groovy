package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.helper.RefreshTokenHasher
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.RefreshTokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.IssuedSession
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult.Reason
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Refresh-Token-Sessions (DIV-65) auf Service-Ebene: Anlegen, Rotation, Sliding bis zur absoluten Grenze, Leerlauf,
 * Kulanzfall und Replay-Erkennung, Widerruf und Aufräumen. Rollback-Isolation; Zeit über testClock
 * (Start 2025-06-15T00:00 UTC). Nebenläufigkeit und Commit-Verhalten: AuthSessionServiceCommitIntegrationSpec.
 * Token-Werte werden nie geloggt, nur Session-/Token-Ids.
 */
class AuthSessionServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(AuthSessionServiceIntegrationSpec)

    private static final String DEVICE = 'device-1'

    @Autowired AuthSessionService service
    @Autowired AuthSessionRepository sessionRepository
    @Autowired RefreshTokenRepository tokenRepository
    @Autowired RefreshTokenHasher hasher

    def "Neue Session: Refresh Token nur als Hash gespeichert, 30 Tage gültig, Session 90 Tage"() {
        given:
        def anna = fixtures.user()
        def start = now()
        LOG.info('Szenario: Nutzer {} meldet sich auf {} an, testClock {}', anna.id, DEVICE, start)

        when:
        def issued = service.createSession(anna, DEVICE)
        flushAndClear()
        def session = sessionRepository.findById(issued.sessionId()).get()
        def tokens = tokenRepository.findBySessionIdOrderById(issued.sessionId())
        LOG.info('createSession -> Session {}, Tokens {}, Ablauf Token {}, Session {}', issued.sessionId(), tokens*.id,
                issued.refreshTokenExpiresAt(), issued.sessionExpiresAt())

        then:
        issued.userId() == anna.id
        issued.refreshToken() ==~ /[A-Za-z0-9_-]{43}/
        !issued.toString().contains(issued.refreshToken())
        session.deviceId == DEVICE
        session.createdAt == start
        session.absoluteExpiresAt == start.plusDays(90)
        session.revokedAt == null
        tokens.size() == 1
        tokens[0].tokenHash == hasher.hash(issued.refreshToken())
        tokens[0].tokenHash != issued.refreshToken()
        tokens[0].expiresAt == start.plusDays(30)
        tokens[0].rotatedAt == null
        issued.refreshTokenExpiresAt() == start.plusDays(30)
        issued.sessionExpiresAt() == start.plusDays(90)
    }

    def "Rotation: alter Token rotiert mit Verweis auf den Nachfolger, neuer Token 30 Tage ab jetzt gültig"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        testClock.advance(Duration.ofDays(10))
        def zeitpunkt = now()
        LOG.info('Szenario: Session {} von Nutzer {}, Refresh nach 10 Tagen ({})', issued.sessionId(), anna.id, zeitpunkt)

        when:
        def result = service.refresh(issued.refreshToken(), DEVICE)
        flushAndClear()
        def tokens = tokenRepository.findBySessionIdOrderById(issued.sessionId())
        def session = sessionRepository.findById(issued.sessionId()).get()
        LOG.info('refresh -> {}, Tokens {}', result.class.simpleName, tokens*.id)

        then:
        result instanceof RefreshResult.Rotated
        def neu = (result as RefreshResult.Rotated).session()
        neu.refreshToken() != issued.refreshToken()
        neu.sessionId() == issued.sessionId()
        neu.refreshTokenExpiresAt() == zeitpunkt.plusDays(30)
        neu.sessionExpiresAt() == issued.sessionExpiresAt()
        tokens.size() == 2
        tokens[0].rotatedAt == zeitpunkt
        tokens[0].replacedBy.id == tokens[1].id
        tokens[1].tokenHash == hasher.hash(neu.refreshToken())
        tokens[1].rotatedAt == null
        tokens[1].expiresAt == zeitpunkt.plusDays(30)
        session.lastUsedAt == zeitpunkt
    }

    def "Sliding: Rotation verlängert höchstens bis zur absoluten Grenze, danach ist kein Refresh mehr möglich (AC8)"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        def grenze = issued.sessionExpiresAt()
        def token = issued.refreshToken()
        [29, 29, 29].each {
            testClock.advance(Duration.ofDays(it))
            token = rotiert(service.refresh(token, DEVICE)).refreshToken()
        }
        LOG.info('Szenario: Session {}, drei Rotationen im Abstand von 29 Tagen (jetzt Tag 87), Grenze {}', issued.sessionId(), grenze)

        when:
        testClock.advance(Duration.ofDays(2))
        def letzte = rotiert(service.refresh(token, DEVICE))
        testClock.advance(Duration.ofDays(1))
        def danach = service.refresh(letzte.refreshToken(), DEVICE)
        LOG.info('refresh an Tag 89 -> Token gültig bis {}; refresh an Tag 90 -> {}', letzte.refreshTokenExpiresAt(), danach)

        then:
        letzte.refreshTokenExpiresAt() == grenze
        danach == new RefreshResult.Rejected(Reason.SESSION_EXPIRED)
    }

    def "Abgelehnt ohne Rotation: #fall"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        vorbereiten(vorbereitung, issued.sessionId(), anna)
        LOG.info('Szenario: Session {} von Nutzer {}, Fall {}', issued.sessionId(), anna.id, fall)

        when:
        def result = service.refresh(issued.refreshToken(), device)
        flushAndClear()
        def tokens = tokenRepository.findBySessionIdOrderById(issued.sessionId())
        LOG.info('refresh -> {}, Tokens {}', result, tokens*.id)

        then:
        result == new RefreshResult.Rejected(grund)
        tokens.size() == 1
        tokens[0].rotatedAt == null

        where:
        fall                                  | device     | vorbereitung  || grund
        'Leerlauf 30 Tage abgelaufen'         | DEVICE     | '+30 Tage'    || Reason.TOKEN_EXPIRED
        'Session widerrufen'                  | DEVICE     | 'widerrufen'  || Reason.SESSION_REVOKED
        'absolute Grenze erreicht'            | DEVICE     | '+90 Tage'    || Reason.SESSION_EXPIRED
        'andere DeviceId'                     | 'device-2' | '-'           || Reason.DEVICE_MISMATCH
        'ohne DeviceId'                       | null       | '-'           || Reason.DEVICE_MISMATCH
        'Nutzer gelöscht (anonymisiert)'      | DEVICE     | 'anonymisiert'|| Reason.USER_DELETED
    }

    def "Falsche DeviceId widerruft die Session nicht, der Token bleibt mit der richtigen DeviceId verwendbar"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        LOG.info('Szenario: Session {} von Nutzer {} auf {}', issued.sessionId(), anna.id, DEVICE)

        when:
        def fremd = service.refresh(issued.refreshToken(), 'device-2')
        def richtig = service.refresh(issued.refreshToken(), DEVICE)
        LOG.info('refresh mit device-2 -> {}, danach mit {} -> {}', fremd, DEVICE, richtig.class.simpleName)

        then:
        fremd == new RefreshResult.Rejected(Reason.DEVICE_MISMATCH)
        richtig instanceof RefreshResult.Rotated
    }

    def "Unbekannter oder leerer Token wird abgelehnt (#fall)"() {
        given:
        service.createSession(fixtures.user(), DEVICE)
        LOG.info('Szenario: eine bestehende Session, Refresh mit {}', fall)

        when:
        def result = service.refresh(token, DEVICE)
        LOG.info('refresh -> {}', result)

        then:
        result == new RefreshResult.Rejected(Reason.UNKNOWN_TOKEN)

        where:
        fall                  | token
        'unbekanntem Token'   | 'A' * 43
        'leerem Token'        | ''
        'null'                | null
    }

    def "Kulanzfall: Vorgänger nach #sekunden s erneut verwendet, Nachfolger unbenutzt -> abgelehnt ohne Widerruf (E5)"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        def nachfolger = rotiert(service.refresh(issued.refreshToken(), DEVICE))
        LOG.info('Szenario: Session {} rotiert, Vorgänger wird {} s später {}x erneut verwendet', issued.sessionId(), sekunden, versuche)

        when:
        testClock.advance(Duration.ofSeconds(sekunden))
        def duplikate = (1..versuche).collect { service.refresh(issued.refreshToken(), DEVICE) }
        def mitNachfolger = service.refresh(nachfolger.refreshToken(), DEVICE)
        flushAndClear()
        def session = sessionRepository.findById(issued.sessionId()).get()
        LOG.info('Duplikate -> {}; Nachfolger -> {}; Session widerrufen {}', duplikate, mitNachfolger.class.simpleName, session.revokedAt != null)

        then:
        duplikate.every { it == new RefreshResult.Rejected(Reason.REUSE_WITHIN_GRACE) }
        session.revokedAt == null
        mitNachfolger instanceof RefreshResult.Rotated

        where:
        sekunden | versuche
        0        | 1
        59       | 1
        30       | 3
    }

    def "Replay: #fall -> Session widerrufen (REUSE_DETECTED), auch der aktuelle Token ist danach ungültig"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        def tokens = [issued.refreshToken()]
        rotationen.times {
            testClock.advance(Duration.ofSeconds(abstand))
            tokens << rotiert(service.refresh(tokens.last(), DEVICE)).refreshToken()
        }
        LOG.info('Szenario: Session {}, {} Rotation(en) im Abstand von {} s, {} erneut verwendet', issued.sessionId(),
                rotationen, abstand, fall)

        when:
        testClock.advance(Duration.ofSeconds(warten))
        def replay = service.refresh(tokens[index], DEVICE)
        def aktuell = service.refresh(tokens.last(), DEVICE)
        flushAndClear()
        def session = sessionRepository.findById(issued.sessionId()).get()
        LOG.info('Replay -> {}; aktueller Token -> {}; Session revokeReason {}', replay, aktuell, session.revokeReason)

        then:
        replay == new RefreshResult.Rejected(Reason.REUSE_DETECTED)
        aktuell == new RefreshResult.Rejected(Reason.SESSION_REVOKED)
        session.revokedAt != null
        session.revokeReason == SessionRevokeReason.REUSE_DETECTED

        where:
        fall                                                   | rotationen | abstand          | warten | index
        'Vorgänger nach genau 60 s'                            | 1          | 0                | 60     | 0
        'Vorgänger nach 61 s'                                  | 1          | 0                | 61     | 0
        'Vorgänger nach Nutzung des Nachfolgers (10 s)'        | 2          | 5                | 0      | 0
        'älterer Token, zwei Rotationen zurück (Tag 3)'        | 3          | 24 * 3600        | 0      | 1
        // F-007: Token aus Tag 1, längst abgelaufen, Session per Rotation aktiv gehalten -> trotzdem Replay
        'abgelaufener rotierter Token an Tag 40'               | 2          | 20 * 24 * 3600   | 0      | 0
    }

    def "Erneuter Login auf demselben Gerät widerruft die alte Session (RELOGIN), andere Geräte bleiben aktiv"() {
        given:
        def anna = fixtures.user()
        def alt = service.createSession(anna, DEVICE)
        def anderesGeraet = service.createSession(anna, 'device-2')
        LOG.info('Szenario: Nutzer {} mit Session {} auf {} und Session {} auf device-2', anna.id, alt.sessionId(),
                DEVICE, anderesGeraet.sessionId())

        when:
        def neu = service.createSession(anna, DEVICE)
        flushAndClear()
        def sessions = [alt, anderesGeraet, neu].collect { sessionRepository.findById(it.sessionId()).get() }
        def refreshAlt = service.refresh(alt.refreshToken(), DEVICE)
        def refreshAnderesGeraet = service.refresh(anderesGeraet.refreshToken(), 'device-2')
        LOG.info('createSession auf {} -> Session {}; revokeReason alt/device-2/neu {}; refresh alt -> {}, device-2 -> {}',
                DEVICE, neu.sessionId(), sessions*.revokeReason, refreshAlt, refreshAnderesGeraet.class.simpleName)

        then:
        sessions*.revokeReason == [SessionRevokeReason.RELOGIN, null, null]
        refreshAlt == new RefreshResult.Rejected(Reason.SESSION_REVOKED)
        refreshAnderesGeraet instanceof RefreshResult.Rotated
    }

    def "revokeAllSessions widerruft nur die aktiven Sessions des Nutzers, bereits widerrufene behalten ihren Grund"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        def annaGeraet1 = service.createSession(anna, DEVICE)
        def annaGeraet2 = service.createSession(anna, 'device-2')
        def annaAbgemeldet = service.createSession(anna, 'device-3')
        service.revokeBySessionId(annaAbgemeldet.sessionId(), SessionRevokeReason.LOGOUT)
        def benSession = service.createSession(ben, DEVICE)
        LOG.info('Szenario: Anna {} mit Sessions {} (aktiv) und {} (abgemeldet), Ben {} mit Session {}', anna.id,
                [annaGeraet1, annaGeraet2]*.sessionId(), annaAbgemeldet.sessionId(), ben.id, benSession.sessionId())

        when:
        def anzahl = service.revokeAllSessions(anna, SessionRevokeReason.PASSWORD_CHANGED)
        flushAndClear()
        def gruende = [annaGeraet1, annaGeraet2, annaAbgemeldet, benSession].collect {
            sessionRepository.findById(it.sessionId()).get().revokeReason
        }
        LOG.info('revokeAllSessions(Anna, PASSWORD_CHANGED) -> {}; Gründe {}', anzahl, gruende)

        then:
        anzahl == 2
        gruende == [SessionRevokeReason.PASSWORD_CHANGED, SessionRevokeReason.PASSWORD_CHANGED, SessionRevokeReason.LOGOUT, null]
    }

    def "Widerruf per Refresh Token (auch rotiert) und per Session-Id ist idempotent; unbekannter Token ändert nichts"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        rotiert(service.refresh(issued.refreshToken(), DEVICE))
        LOG.info('Szenario: Session {} von Nutzer {}, erster Token bereits rotiert', issued.sessionId(), anna.id)

        when:
        def perToken = service.revokeByRefreshToken(issued.refreshToken(), SessionRevokeReason.LOGOUT)
        def nochmal = service.revokeBySessionId(issued.sessionId(), SessionRevokeReason.LOGOUT)
        def unbekannt = service.revokeByRefreshToken('B' * 43, SessionRevokeReason.LOGOUT)
        def leer = service.revokeByRefreshToken(null, SessionRevokeReason.LOGOUT)
        flushAndClear()
        def session = sessionRepository.findById(issued.sessionId()).get()
        LOG.info('revokeByRefreshToken -> {}, revokeBySessionId erneut -> {}, unbekannt -> {}, null -> {}; Grund {}',
                perToken, nochmal, unbekannt, leer, session.revokeReason)

        then:
        perToken == Optional.of(issued.sessionId())
        !nochmal
        unbekannt.empty
        leer.empty
        session.revokeReason == SessionRevokeReason.LOGOUT
    }

    def "deleteAllForUser löscht alle Sessions und Refresh Tokens des Nutzers, andere Nutzer bleiben"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        def annaSession = service.createSession(anna, DEVICE)
        rotiert(service.refresh(annaSession.refreshToken(), DEVICE))
        def benSession = service.createSession(ben, DEVICE)
        flushAndClear()
        LOG.info('Szenario: Anna {} mit Session {} (2 Tokens), Ben {} mit Session {}', anna.id, annaSession.sessionId(),
                ben.id, benSession.sessionId())

        when:
        def geloescht = service.deleteAllForUser(anna)
        flushAndClear()
        def annaTokens = tokenRepository.findBySessionIdOrderById(annaSession.sessionId())
        def benTokens = tokenRepository.findBySessionIdOrderById(benSession.sessionId())
        LOG.info('deleteAllForUser(Anna) -> {}; Tokens Anna {}, Ben {}', geloescht, annaTokens*.id, benTokens*.id)

        then:
        geloescht == 1
        sessionRepository.findById(annaSession.sessionId()).empty
        annaTokens.empty
        sessionRepository.findById(benSession.sessionId()).present
        benTokens.size() == 1
    }

    def "Aufräumen löscht seit mehr als 7 Tagen beendete Sessions samt Tokens, aktive und kürzlich beendete bleiben"() {
        given: "alle Sessions an Tag 0 angelegt; Zeitpunkte direkt gesetzt, jetzt = Tag 100"
        def anna = fixtures.user()
        def tag0 = now()
        def jetzt = tag0.plusDays(100)
        def faelle = [
                'aktiv'                         : [:],
                'widerrufen vor 8 Tagen'        : [revokedAt: jetzt.minusDays(8)],
                'widerrufen vor 6 Tagen'        : [revokedAt: jetzt.minusDays(6)],
                'absolut abgelaufen vor 8 Tagen': [absoluteExpiresAt: jetzt.minusDays(8)],
                'Leerlauf abgelaufen vor 8 Tagen': [tokenExpiresAt: jetzt.minusDays(8)],
                'Leerlauf abgelaufen vor 6 Tagen': [tokenExpiresAt: jetzt.minusDays(6)],
        ]
        def ids = faelle.collectEntries { fall, werte ->
            def issued = service.createSession(anna, "device-$fall".toString())
            def session = sessionRepository.findById(issued.sessionId()).get()
            session.absoluteExpiresAt = werte.absoluteExpiresAt ?: jetzt.plusDays(50)
            session.revokedAt = werte.revokedAt
            def token = tokenRepository.findBySessionIdOrderById(issued.sessionId())[0]
            token.expiresAt = werte.tokenExpiresAt ?: jetzt.plusDays(20)
            [(fall): issued.sessionId()]
        }
        // rotierter Token einer aktiven Session bleibt (Replay-Zuordnung)
        def aktivToken = tokenRepository.findBySessionIdOrderById(ids['aktiv'])[0]
        aktivToken.rotatedAt = tag0
        def nachfolger = tokenRepository.save(TestData.refreshToken(
                session: aktivToken.session, expiresAt: jetzt.plusDays(20)))
        aktivToken.replacedBy = nachfolger
        flushAndClear()
        testClock.advance(Duration.between(tag0, jetzt))
        LOG.info('Szenario: Nutzer {}, Sessions {}, jetzt {}', anna.id, ids, jetzt)

        when:
        def geloescht = service.deleteEndedSessions()
        flushAndClear()
        def uebrig = ids.findAll { fall, id -> sessionRepository.findById(id).present }.keySet()
        def aktivTokens = tokenRepository.findBySessionIdOrderById(ids['aktiv'])*.id
        LOG.info('deleteEndedSessions -> {}; übrig {}, Tokens der aktiven Session {}', geloescht, uebrig, aktivTokens)

        then:
        // Gesamtzahl enthält ggf. liegengebliebene Sessions der Commit-Specs, daher nur Untergrenze
        geloescht >= 3
        uebrig == ['aktiv', 'widerrufen vor 6 Tagen', 'Leerlauf abgelaufen vor 6 Tagen'] as Set
        aktivTokens.size() == 2
    }

    private static IssuedSession rotiert(RefreshResult result) {
        assert result instanceof RefreshResult.Rotated
        (result as RefreshResult.Rotated).session()
    }

    private void vorbereiten(String vorbereitung, Long sessionId, User user) {
        switch (vorbereitung) {
            case '+30 Tage': testClock.advance(Duration.ofDays(30)); break
            case '+90 Tage': testClock.advance(Duration.ofDays(90)); break
            case 'widerrufen': service.revokeBySessionId(sessionId, SessionRevokeReason.LOGOUT); break
            case 'anonymisiert': user.name = UserEnum.USER_DELETED.name; break
            case '-': break
            default: throw new IllegalArgumentException(vorbereitung)
        }
    }

    private LocalDateTime now() {
        LocalDateTime.ofInstant(testClock.instant(), ZoneOffset.UTC)
    }
}
