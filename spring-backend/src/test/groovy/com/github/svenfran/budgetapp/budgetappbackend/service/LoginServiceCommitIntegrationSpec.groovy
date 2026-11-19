package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp
import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException
import com.github.svenfran.budgetapp.budgetappbackend.helper.LoginAttemptKeys
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.BadCredentialsException
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
 * Commit-Grenzen des Logins mit Rate-Limit (DIV-64, Finding F-003): LoginService ist nicht transaktional, der Zähler
 * committet vor der Passwortprüfung und überlebt den Rollback des fehlgeschlagenen Logins; eine Abweisung ändert
 * keinen Zähler; ein Erfolg gibt die eigene Reservierung committet frei; eine Freigabe ohne Zeile ist ein No-op.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature. IPs aus 192.0.2.0/24 (nicht 127.0.0.1, das
 * die API-Specs nutzen – die Zeilen bleiben nach dem Feature liegen).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginServiceCommitIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(LoginServiceCommitIntegrationSpec)

    private static final long TIMEOUT = 20
    private static final String PASSWORT = 'geheim123'
    private static final String DEVICE = 'device-1'

    @Autowired LoginService loginService
    @Autowired LoginRateLimiter rateLimiter
    @Autowired LoginAttemptKeys keys
    @Autowired PasswordEncoder passwordEncoder
    @Autowired TokenRepository tokenRepository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    def setup() {
        databaseCleaner.truncateAll()
    }

    def "Fehlversuche bleiben gezählt, obwohl jede Login-Transaktion zurückgerollt wird"() {
        given:
        def email = nutzer('lsc-fehler@example.org')
        def ip = '192.0.2.1'
        LOG.info('Szenario: Nutzer lsc-fehler, 5 Fehlversuche von {}', ip)

        when:
        def fehler = (1..5).collect { abgefangen { login(email, 'falsch', ip) } }
        def zaehler = zaehlerstaende(email, ip)
        LOG.info('5x loginService.authenticate -> {}; Zähler committet {}', fehler*.class*.simpleName, zaehler)

        then:
        fehler.every { it instanceof BadCredentialsException }
        zaehler == [IP: 5, ACCOUNT: 5, ACCOUNT_IP: 5]
    }

    def "Der 6. Versuch wird abgewiesen und ändert keinen Zähler"() {
        given:
        def email = nutzer('lsc-abweisung@example.org')
        def ip = '192.0.2.2'
        5.times { abgefangen { login(email, 'falsch', ip) } }
        def vorher = zeilen()
        testClock.advance(Duration.ofMinutes(1))
        LOG.info('Szenario: Nutzer lsc-abweisung, 5 Fehlversuche von {} -> {}; Uhr +1 min', ip, vorher)

        when:
        def ergebnis = abgefangen { login(email, PASSWORT, ip) }
        def nachher = zeilen()
        LOG.info('6. Versuch (richtiges Passwort) -> {}; Zeilen {}', ergebnis.class.simpleName, nachher)

        then:
        ergebnis instanceof TooManyLoginAttemptsException
        nachher == vorher
    }

    def "Erfolg nach 2 Fehlversuchen: Token gespeichert, eigene Reservierung committet freigegeben"() {
        given:
        def email = nutzer('lsc-erfolg@example.org')
        def userId = userIdVon(email)
        def ip = '192.0.2.3'
        2.times { abgefangen { login(email, 'falsch', ip) } }
        LOG.info('Szenario: Nutzer {} (lsc-erfolg), 2 Fehlversuche von {} -> {}', userId, ip, zaehlerstaende(email, ip))

        when:
        def antwort = login(email, PASSWORT, ip) as AuthenticationResponse
        def tokens = tokenRepository.findAllByUserId(userId)*.deviceId
        def zaehler = zaehlerstaende(email, ip)
        LOG.info('Login -> Nutzer {}; Tokens (DeviceIds) {}; Zähler {}', antwort.id, tokens, zaehler)

        then:
        antwort.id == userId
        tokens == [DEVICE]
        zaehler == [IP: 2, ACCOUNT: 2, ACCOUNT_IP: null]
    }

    def "Freigabe ohne Zeile (zwischen Reservierung und Freigabe aufgeräumt) ist ein No-op, der Login gelingt"() {
        given:
        def email = nutzer('lsc-noop@example.org')
        def ip = '192.0.2.4'
        def gesperrt = new CountDownLatch(1)
        def freigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        def transaction = new TransactionTemplate(transactionManager)
        LOG.info('Szenario: Nutzer lsc-noop von {}; T1 sperrt die Tabelle token, der Login wartet nach der Reservierung darauf', ip)

        when:
        Map<String, Integer> waehrendLogin
        AuthenticationResponse antwort
        try {
            def t1 = pool.submit({
                transaction.execute {
                    jdbcTemplate.execute('LOCK TABLE token IN ACCESS EXCLUSIVE MODE')
                    gesperrt.countDown()
                    assert freigabe.await(TIMEOUT, TimeUnit.SECONDS)
                }
            } as Callable)
            assert gesperrt.await(TIMEOUT, TimeUnit.SECONDS)
            def anmeldung = pool.submit({ login(email, PASSWORT, ip) } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            testClock.advance(Duration.ofMinutes(15))
            rateLimiter.deleteExpiredAttempts()
            waehrendLogin = zaehlerstaende(email, ip)
            freigabe.countDown()
            t1.get(TIMEOUT, TimeUnit.SECONDS)
            antwort = anmeldung.get(TIMEOUT, TimeUnit.SECONDS) as AuthenticationResponse
        } finally {
            freigabe.countDown()
            pool.shutdownNow()
        }
        def danach = zaehlerstaende(email, ip)
        LOG.info('Login wartet; Uhr +15 min, deleteExpiredAttempts() -> {}; Login -> Nutzer {}; Zähler danach {}',
                waehrendLogin, antwort.id, danach)

        then:
        waehrendLogin == [IP: null, ACCOUNT: null, ACCOUNT_IP: null]
        antwort.token
        danach == [IP: null, ACCOUNT: null, ACCOUNT_IP: null]
    }

    /** Legt einen Nutzer mit Passwort geheim123 an (committet) und liefert seine E-Mail. */
    private String nutzer(String email) {
        fixtures.user(email: email, password: passwordEncoder.encode(PASSWORT)).email
    }

    private Long userIdVon(String email) {
        jdbcTemplate.queryForObject('select id from user_details where email = ?', Long, email)
    }

    private Object login(String email, String passwort, String ip) {
        def request = new AuthenticationRequest(email, passwort, DEVICE)
        loginService.authenticate(request, new BeanPropertyBindingResult(request, 'request'),
                new ClientIp(ip, ClientIp.Source.HEADER))
    }

    private static Object abgefangen(Closure aufruf) {
        try {
            aufruf.call()
        } catch (Exception e) {
            e
        }
    }

    /** Zählerstand je Scope (null = keine Zeile). */
    private Map<String, Integer> zaehlerstaende(String email, String ip) {
        LoginAttemptScope.values().collectEntries { scope ->
            def werte = jdbcTemplate.queryForList('select attempts from login_attempt where scope = ? and key_hash = ?',
                    Integer, scope.name(), keys.keyHash(scope, email, ip))
            [scope.name(), werte ? werte[0] : null]
        }
    }

    /** Alle Zeilen (scope, attempts, window_start), sortiert. */
    private List<String> zeilen() {
        jdbcTemplate.queryForList('select scope, attempts, window_start from login_attempt order by scope')
                .collect { "${it.scope}=${it.attempts}@${it.window_start}".toString() }
    }

    /** Zahl der Datenbankverbindungen, die gerade auf eine Sperre warten. */
    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }
}
