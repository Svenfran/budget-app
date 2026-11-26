package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse
import com.github.svenfran.budgetapp.budgetappbackend.dto.PasswordChangeDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.validation.BeanPropertyBindingResult
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Login gegen Passwortänderung und Konto löschen (DIV-65, E4/B13): Beide sperren zuerst die Nutzerzeile. Wer zuerst
 * sperrt, gewinnt; der andere wartet nachweislich (pg_stat_activity) und sieht danach den committeten Stand. In keiner
 * Reihenfolge bleibt eine aktive Session übrig, die mit dem alten Passwort bzw. für das gelöschte Konto entstanden ist.
 * Dazu: parallele Logins derselben DeviceId und das Aufräumen beendeter Sessions im Cron-Job.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature. Überlappung wie in
 * AuthSessionServiceCommitIntegrationSpec: Transaktion 1 hält ihre Sperren offen, bis Transaktion 2 auf eine Sperre
 * wartet. Profilaktionen laufen mit dem Nutzer im SecurityContext des jeweiligen Threads.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthenticationServiceCommitIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationServiceCommitIntegrationSpec)

    private static final long TIMEOUT = 20
    private static final String DEVICE = 'device-1'
    private static final String EMAIL = 'asc-anna@example.org'

    @Autowired AuthenticationService authenticationService
    @Autowired UserProfileService userProfileService
    @Autowired AuthSessionService authSessionService
    @Autowired PasswordEncoder passwordEncoder
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Login mit altem Passwort und Passwortänderung überlappend (#erster zuerst): keine aktive Session, #erwartet"() {
        given:
        def anna = fixtures.user(email: EMAIL, password: passwordEncoder.encode('alt'))
        def aufrufe = [login          : { login('alt') },
                       passwortwechsel: { alsAngemeldet(anna) { userProfileService.changePassword(new PasswordChangeDto(anna.id, 'alt', 'neu')) } }]
        LOG.info('Szenario: Nutzer {} mit Passwort alt; T1 {} hält die Nutzersperre, T2 {}', anna.id, erster, zweiter)

        when:
        def (eins, zwei) = overlapping(aufrufe[erster], aufrufe[zweiter])
        def loginErgebnis = erster == 'login' ? eins : zwei
        def aktiv = aktiveSessions(anna.id)
        def gruende = widerrufsgruende(anna.id)
        LOG.info('T1 -> {}, T2 -> {}; aktive Sessions {}, Widerrufsgründe {}', name(eins), name(zwei), aktiv, gruende)

        then:
        aktiv == []
        loginErgebnis.class == loginKlasse
        gruende == erwarteteGruende

        where:
        erster            | zweiter           || loginKlasse             | erwarteteGruende                       | erwartet
        'passwortwechsel' | 'login'           || BadCredentialsException | []                                     | 'Login scheitert am neuen Passwort'
        'login'           | 'passwortwechsel' || AuthenticationResponse  | [SessionRevokeReason.PASSWORD_CHANGED] | 'neue Session wird widerrufen'
    }

    def "Login und Konto löschen überlappend (#erster zuerst): keine Session übrig, #erwartet"() {
        given:
        def anna = fixtures.user(email: EMAIL, password: passwordEncoder.encode('alt'))
        def aufrufe = [login   : { login('alt') },
                       loeschen: { alsAngemeldet(anna) { userProfileService.deleteUserProfile(anna.id) } }]
        LOG.info('Szenario: Nutzer {}; T1 {} hält die Nutzersperre, T2 {}', anna.id, erster, zweiter)

        when:
        def (eins, zwei) = overlapping(aufrufe[erster], aufrufe[zweiter])
        def loginErgebnis = erster == 'login' ? eins : zwei
        def sessions = jdbcTemplate.queryForObject('select count(*) from auth_session where user_id = ?', Integer, anna.id)
        LOG.info('T1 -> {}, T2 -> {}; Sessions des Nutzers {}', name(eins), name(zwei), sessions)

        then:
        sessions == 0
        loginErgebnis.class == loginKlasse

        where:
        erster     | zweiter    || loginKlasse             | erwartet
        'loeschen' | 'login'    || BadCredentialsException | 'Login findet die alte E-Mail nicht mehr'
        'login'    | 'loeschen' || AuthenticationResponse  | 'neue Session wird gelöscht'
    }

    def "Zwei überlappende Logins auf demselben Gerät: genau eine aktive Session"() {
        given:
        def anna = fixtures.user(email: EMAIL, password: passwordEncoder.encode('alt'))
        LOG.info('Szenario: Nutzer {}, T1 und T2 melden sich auf {} an', anna.id, DEVICE)

        when:
        def (eins, zwei) = overlapping({ login('alt') }, { login('alt') })
        def aktiv = aktiveSessions(anna.id)
        def gruende = widerrufsgruende(anna.id)
        LOG.info('T1 -> {}, T2 -> {}; aktive Sessions {}, Widerrufsgründe {}', name(eins), name(zwei), aktiv, gruende)

        then:
        eins instanceof AuthenticationResponse
        zwei instanceof AuthenticationResponse
        aktiv.size() == 1
        gruende == [SessionRevokeReason.RELOGIN]
    }

    def "Cron-Job löscht seit mehr als 7 Tagen beendete Sessions samt Refresh Tokens, aktive bleiben"() {
        given:
        def anna = fixtures.user()
        def widerrufen = authSessionService.createSession(anna, 'device-1').sessionId()
        authSessionService.revokeBySessionId(widerrufen, SessionRevokeReason.LOGOUT)
        def leerlauf = authSessionService.createSession(anna, 'device-2').sessionId()
        testClock.advance(Duration.ofDays(38))
        def aktiv = authSessionService.createSession(anna, 'device-3').sessionId()
        LOG.info('Szenario: Nutzer {}; Session {} an Tag 0 widerrufen, Session {} seit Tag 30 im Leerlauf abgelaufen, '
                + 'Session {} an Tag 38 angelegt; Job an Tag 38', anna.id, widerrufen, leerlauf, aktiv)

        when:
        authSessionService.deleteEndedSessions()
        def sessions = jdbcTemplate.queryForList('select id from auth_session where user_id = ? order by id', Long, anna.id)
        def tokens = jdbcTemplate.queryForList(
                'select distinct session_id from refresh_token where session_id in (?, ?, ?) order by session_id', Long,
                widerrufen, leerlauf, aktiv)
        LOG.info('deleteEndedSessions() (Cron) -> Sessions {}, Sessions mit Refresh Tokens {}', sessions, tokens)

        then:
        sessions == [aktiv]
        tokens == [aktiv]
    }

    private AuthenticationResponse login(String passwort) {
        def request = new AuthenticationRequest(EMAIL, passwort, DEVICE)
        authenticationService.authenticate(request, new BeanPropertyBindingResult(request, 'request'))
    }

    /** Führt den Aufruf mit dem Nutzer im SecurityContext des aktuellen Threads aus. */
    private static Object alsAngemeldet(User user, Closure aufruf) {
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(user, null, user.authorities)
        try {
            aufruf.call()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    /**
     * Führt {@code erster} in Transaktion 1 aus und hält sie offen, startet {@code zweiter} (eigene Transaktion), wartet,
     * bis dieser auf eine Sperre wartet, und committet dann 1. Liefert beide Ergebnisse; eine Exception ist das Ergebnis.
     */
    private List overlapping(Closure erster, Closure zweiter) {
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                abgefangen {
                    transaction.execute {
                        def ergebnis = erster.call()
                        ersterAusgefuehrt.countDown()
                        assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                        ergebnis
                    }
                }
            } as Callable)
            assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
            def t2 = pool.submit({ abgefangen(zweiter) } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            commitFreigabe.countDown()
            [t1.get(TIMEOUT, TimeUnit.SECONDS), t2.get(TIMEOUT, TimeUnit.SECONDS)]
        } finally {
            commitFreigabe.countDown()
            pool.shutdownNow()
        }
    }

    private static Object abgefangen(Closure aufruf) {
        try {
            aufruf.call()
        } catch (Exception e) {
            e
        }
    }

    private static String name(Object ergebnis) {
        ergebnis == null ? 'ok (void)' : ergebnis.class.simpleName
    }

    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }

    private List<Long> aktiveSessions(Long userId) {
        jdbcTemplate.queryForList('select id from auth_session where user_id = ? and revoked_at is null order by id',
                Long, userId)
    }

    private List<SessionRevokeReason> widerrufsgruende(Long userId) {
        jdbcTemplate.queryForList(
                'select revoke_reason from auth_session where user_id = ? and revoked_at is not null order by id',
                String, userId).collect { SessionRevokeReason.valueOf(it) }
    }
}
