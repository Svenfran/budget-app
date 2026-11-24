package com.github.svenfran.budgetapp.budgetappbackend.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException
import com.github.svenfran.budgetapp.budgetappbackend.helper.LoginAttemptKeys
import com.github.svenfran.budgetapp.budgetappbackend.repository.LoginAttemptRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Transaktions- und Nebenläufigkeitsverhalten des Login-Rate-Limits (DIV-64, AC7): Parallele Versuche kommen nicht
 * über das Limit, abgewiesene Versuche werden vollständig zurückgerollt, OBSERVE zählt wie ENFORCE ohne abzuweisen,
 * Reservieren und Freigeben nutzen dieselbe Sperrreihenfolge (warten aufeinander statt Deadlock), das Aufräumen
 * wartet nie auf gesperrte Zeilen.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; jeder Limiter-Aufruf ohne äußere Transaktion
 * committet einzeln (wie in Produktion). Überlappung wie in ShoppingReorderConcurrencyIntegrationSpec: Transaktion 1
 * hält ihre Sperren, Transaktion 2 muss nachweislich warten (pg_stat_activity), erst dann committet 1.
 * IPs aus 203.0.113.0/24, E-Mails mit Präfix "lrlc-" (die Zeilen bleiben nach dem Feature liegen).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginRateLimiterConcurrencyIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiterConcurrencyIntegrationSpec)

    /** Sicherheitstimeout in Sekunden für Latches, das Warten auf die Sperre und die Threads. */
    private static final long TIMEOUT = 20

    @Autowired LoginRateLimiter rateLimiter
    @Autowired LoginAttemptKeys keys
    @Autowired LoginAttemptRepository repository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Parallele Versuche (#szenario): genau #erfolgreich kommen durch, abgewiesene hinterlassen keine Zeilen"() {
        given:
        def versuche = (0..<anzahl).collect { i ->
            def email = accounts == 1 ? 'lrlc-parallel@example.org' : "lrlc-parallel-${i}@example.org".toString()
            def ip = ips == 1 ? '203.0.113.1' : "203.0.113.${i + 10}".toString()
            [email, ip]
        }
        LOG.info('Szenario: {} parallele acquire, {} Account(s), {} IP(s)', anzahl, accounts, ips)

        when:
        def ergebnisse = parallel(versuche.collect { v -> { -> abgewiesen { rateLimiter.acquire(v[0], header(v[1])) } } })
        def durchgekommen = ergebnisse.count { it instanceof LoginReservation }
        def abgewieseneAnzahl = ergebnisse.count { it instanceof TooManyLoginAttemptsException }
        def stand = zeilenJeScope()
        LOG.info('-> durchgekommen {}, abgewiesen {}, Zeilen je Scope {}', durchgekommen, abgewieseneAnzahl, stand)

        then:
        durchgekommen == erfolgreich
        abgewieseneAnzahl == anzahl - erfolgreich
        stand == [IP: ipZeilen, ACCOUNT: accountZeilen, ACCOUNT_IP: accountIpZeilen]

        where:
        szenario                                 | anzahl | accounts | ips || erfolgreich | ipZeilen  | accountZeilen | accountIpZeilen
        'gleicher Account, gleiche IP'           | 20     | 1        | 1   || 5           | [5]       | [5]           | [5]
        'gleicher Account, verschiedene IPs'     | 30     | 1        | 30  || 20          | [1] * 20  | [20]          | [1] * 20
        'verschiedene Accounts, gleiche IP'      | 40     | 40       | 1   || 30          | [30]      | [1] * 30      | [1] * 30
    }

    def "Abweisung ändert weder Zähler noch Fensterbeginn"() {
        given:
        def email = 'lrlc-abweisung@example.org'
        def ip = '203.0.113.2'
        5.times { rateLimiter.acquire(email, header(ip)) }
        def vorher = zeilen()
        testClock.advance(Duration.ofMinutes(1))
        LOG.info('Szenario: 5 Versuche committet -> {}; Uhr +1 min', vorher)

        when:
        def ergebnisse = (1..3).collect { abgewiesen { rateLimiter.acquire(email, header(ip)) } }
        def nachher = zeilen()
        LOG.info('3x acquire -> {}; Zeilen {}', ergebnisse*.class*.simpleName, nachher)

        then:
        ergebnisse.every { it instanceof TooManyLoginAttemptsException }
        nachher == vorher
    }

    def "Modus OBSERVE zählt wie ENFORCE, weist aber nicht ab und loggt would_block"() {
        given:
        def observe = new LoginRateLimiter(repository, keys, transactionManager, testClock, 'OBSERVE', '15m', 5, 20, 30, 500)
        def email = 'lrlc-observe@example.org'
        def ip = '203.0.113.3'
        def appender = new ListAppender<ILoggingEvent>()
        def limiterLogger = LoggerFactory.getLogger(LoginRateLimiter) as LogbackLogger
        LOG.info('Szenario: Limiter im Modus OBSERVE, 7 Versuche lrlc-observe von {}', ip)

        when:
        appender.start()
        limiterLogger.addAppender(appender)
        List<LoginReservation> reservierungen
        try {
            reservierungen = (1..7).collect { observe.acquire(email, header(ip)) }
        } finally {
            limiterLogger.detachAppender(appender)
        }
        def warnungen = appender.list.findAll { it.level == Level.WARN }*.formattedMessage
        def stand = zeilenJeScope()
        LOG.info('7x acquire -> leer {}, Zeilen {}, WARN {}', reservierungen*.empty, stand, warnungen)

        then:
        reservierungen*.empty == [false] * 5 + [true] * 2
        stand == [IP: [5], ACCOUNT: [5], ACCOUNT_IP: [5]]
        warnungen.size() == 2
        warnungen.every { it.startsWith('event=login_rate_limit_would_block scope=ACCOUNT_IP ') }
    }

    def "Reservieren und Freigeben überlappend (Variante #variante): Transaktion 2 wartet, kein Deadlock, Endzustand je Scope"() {
        given:
        def email = 'lrlc-overlap@example.org'
        def ip = '203.0.113.4'
        def r0 = rateLimiter.acquire(email, header(ip))
        def aufrufe = [acquire: { rateLimiter.acquire(email, header(ip)) }, recordSuccess: { rateLimiter.recordSuccess(r0) }]
        LOG.info('Szenario: R0 committet ({}), T1 {} hält offen, T2 {}', zeilen(), erster, zweiter)

        when:
        overlapping(aufrufe[erster], aufrufe[zweiter])
        def stand = zeilenJeScope()
        LOG.info('beide committet -> Zeilen je Scope {}', stand)

        then:
        stand == erwartet

        where:
        variante | erster          | zweiter         || erwartet
        'a'      | 'acquire'       | 'recordSuccess' || [IP: [1], ACCOUNT: [1], ACCOUNT_IP: []]
        'b'      | 'recordSuccess' | 'acquire'       || [IP: [1], ACCOUNT: [1], ACCOUNT_IP: [1]]
    }

    def "Aufräumen wartet nicht auf eine gesperrte Zeile, löscht die übrigen und die gesperrte beim nächsten Lauf"() {
        given:
        def abgelaufen = testClockUtc() - Duration.ofMinutes(16)
        def (k1, k2, k3) = ['lrlc-cleanup-1', 'lrlc-cleanup-2', 'lrlc-cleanup-3']
        [k1, k2, k3].each { zeileAnlegen(it, abgelaufen) }
        def gesperrt = new CountDownLatch(1)
        def freigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        LOG.info('Szenario: 3 abgelaufene IP-Zeilen {}, T1 sperrt {} per SELECT FOR UPDATE und hält offen', [k1, k2, k3], k2)

        when:
        List<String> waehrendSperre
        int wartendWaehrendCleanup
        try {
            def t1 = pool.submit({
                transaction.execute {
                    jdbcTemplate.queryForList("select attempts from login_attempt where scope = 'IP' and key_hash = ? for update", k2)
                    gesperrt.countDown()
                    assert freigabe.await(TIMEOUT, TimeUnit.SECONDS)
                }
            } as Callable)
            assert gesperrt.await(TIMEOUT, TimeUnit.SECONDS)
            pool.submit({ rateLimiter.deleteExpiredAttempts() } as Callable).get(5, TimeUnit.SECONDS)
            wartendWaehrendCleanup = wartendAufSperre()
            waehrendSperre = keyHashes()
            freigabe.countDown()
            t1.get(TIMEOUT, TimeUnit.SECONDS)
        } finally {
            freigabe.countDown()
            pool.shutdownNow()
        }
        rateLimiter.deleteExpiredAttempts()
        def danach = keyHashes()
        LOG.info('Cleanup während der Sperre fertig (wartend {}) -> {}; nach Freigabe erneut -> {}', wartendWaehrendCleanup, waehrendSperre, danach)

        then:
        wartendWaehrendCleanup == 0
        waehrendSperre == [k2]
        danach == []
    }

    /** Startet alle Aufrufe gleichzeitig (Latch) und liefert ihre Ergebnisse; ein unerwarteter Fehler scheitert über Future.get. */
    private List parallel(List<Closure> aufrufe) {
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(aufrufe.size())
        try {
            def futures = aufrufe.collect { aufruf ->
                pool.submit({
                    assert start.await(TIMEOUT, TimeUnit.SECONDS)
                    aufruf.call()
                } as Callable)
            }
            start.countDown()
            futures.collect { it.get(TIMEOUT, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Führt {@code erster} in einer äußeren Transaktion aus und hält sie offen, startet {@code zweiter} (ohne äußere
     * Transaktion, also mit eigener) in einem zweiten Thread, wartet, bis dieser an einer Zeilensperre hängt, und gibt
     * dann den Commit von 1 frei.
     */
    private void overlapping(Closure erster, Closure zweiter) {
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                transaction.execute {
                    erster.call()
                    ersterAusgefuehrt.countDown()
                    assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                }
            } as Callable)
            assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
            def t2 = pool.submit({ zweiter.call() } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            commitFreigabe.countDown()
            t1.get(TIMEOUT, TimeUnit.SECONDS)
            t2.get(TIMEOUT, TimeUnit.SECONDS)
        } finally {
            commitFreigabe.countDown()
            pool.shutdownNow()
        }
    }

    private static Object abgewiesen(Closure aufruf) {
        try {
            aufruf.call()
        } catch (TooManyLoginAttemptsException e) {
            e
        }
    }

    private LocalDateTime testClockUtc() {
        LocalDateTime.ofInstant(testClock.instant(), ZoneOffset.UTC)
    }

    /** Zählerstände je Scope, sortiert (die Tabelle enthält nur die Zeilen dieses Features). */
    private Map<String, List<Integer>> zeilenJeScope() {
        def zeilen = jdbcTemplate.queryForList('select scope, attempts from login_attempt')
        ['IP', 'ACCOUNT', 'ACCOUNT_IP'].collectEntries { scope ->
            [scope, zeilen.findAll { it.scope == scope }*.attempts.sort()]
        }
    }

    /** Alle Zeilen (scope, attempts, window_start), sortiert. */
    private List<String> zeilen() {
        jdbcTemplate.queryForList('select scope, attempts, window_start from login_attempt order by scope')
                .collect { "${it.scope}=${it.attempts}@${it.window_start}".toString() }
    }

    private List<String> keyHashes() {
        jdbcTemplate.queryForList('select key_hash from login_attempt order by key_hash', String)
    }

    private void zeileAnlegen(String keyHash, LocalDateTime windowStart) {
        jdbcTemplate.update('insert into login_attempt (scope, key_hash, window_start, attempts) values (?, ?, ?, 1)',
                'IP', keyHash, windowStart)
    }

    /** Zahl der Datenbankverbindungen, die gerade auf eine Sperre warten. */
    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }

    /** Client-IP wie vom ClientIpResolver aus dem Edge-Header ermittelt. */
    private static ClientIp header(String ip) {
        new ClientIp(ip, ClientIp.Source.HEADER)
    }
}
