package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException
import com.github.svenfran.budgetapp.budgetappbackend.helper.LoginAttemptKeys
import com.github.svenfran.budgetapp.budgetappbackend.repository.LoginAttemptRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.LoginAttemptRepository.AttemptCount
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT_IP
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.IP

/**
 * Regeln des Login-Rate-Limits ohne Datenbank (DIV-64): Konfigurationsvalidierung, Modi, Sperrreihenfolge
 * IP → ACCOUNT → ACCOUNT_IP für Reservieren und Freigeben, Rollback-Markierung bei Überschreitung, Retry-After.
 * Repository und Transaktionsmanager sind Mocks; das TransactionTemplate des Limiters ruft getTransaction/commit auf.
 */
class LoginRateLimiterSpec extends Specification {

    /** Dummy-Secret, nur für Tests. */
    static final String SECRET = 'bG9naW4tcmF0ZS1saW1pdGVyLXNwZWMtZHVtbXktc2VjcmV0'
    static final String EMAIL = 'max@example.de'
    static final String CLIENT_IP = '203.0.113.7'
    static final Instant JETZT = Instant.parse('2025-06-15T10:00:00Z')
    static final LocalDateTime NOW = LocalDateTime.ofInstant(JETZT, ZoneOffset.UTC)

    LoginAttemptRepository repository = Mock()
    PlatformTransactionManager transactionManager = Mock()
    TransactionStatus status = Mock()
    def keys = new LoginAttemptKeys(SECRET)

    def setup() {
        transactionManager.getTransaction(_) >> status
    }

    def "Ungültige Konfiguration (#fall) verhindert den Start"() {
        when:
        limiter(mode: mode, window: window, accountIpLimit: accountIp, accountLimit: account, ipLimit: ip)

        then:
        thrown(IllegalStateException)

        where:
        fall                 | mode      | window | accountIp | account | ip
        'unbekannter Modus'  | 'BLOCK'   | '15m'  | 5         | 20      | 30
        'leerer Modus'       | ''        | '15m'  | 5         | 20      | 30
        'Fenster 0'          | 'ENFORCE' | '0s'   | 5         | 20      | 30
        'negatives Fenster'  | 'ENFORCE' | '-1m'  | 5         | 20      | 30
        'Fenster kein Wert'  | 'ENFORCE' | 'abc'  | 5         | 20      | 30
        'Account+IP-Limit 0' | 'ENFORCE' | '15m'  | 0         | 20      | 30
        'Account-Limit 0'    | 'ENFORCE' | '15m'  | 5         | 0       | 30
        'IP-Limit 0'         | 'ENFORCE' | '15m'  | 5         | 20      | 0
    }

    def "Modus wird ohne Rücksicht auf Groß-/Kleinschreibung gelesen"() {
        when:
        limiter(mode: ' observe ')

        then:
        noExceptionThrown()
    }

    def "Modus OFF zählt nicht und gibt nichts frei"() {
        given:
        def limiter = limiter(mode: 'OFF')

        when:
        def reservation = limiter.acquire(EMAIL, header(CLIENT_IP))
        limiter.recordSuccess(reservation)

        then:
        reservation.empty
        reservation.accountPseudonym() == keys.accountPseudonym(EMAIL)
        0 * repository._
        0 * transactionManager._
    }

    def "Reservieren zählt in Sperrreihenfolge IP, ACCOUNT, ACCOUNT_IP in einer Transaktion"() {
        given:
        def limiter = limiter()

        when:
        def reservation = limiter.acquire(EMAIL, header(CLIENT_IP))

        then:
        1 * repository.increment(IP, keys.keyHash(IP, EMAIL, CLIENT_IP), NOW, NOW - Duration.ofMinutes(15)) >> new AttemptCount(1, NOW)

        then:
        1 * repository.increment(ACCOUNT, keys.keyHash(ACCOUNT, EMAIL, CLIENT_IP), NOW, _) >> new AttemptCount(1, NOW)

        then:
        1 * repository.increment(ACCOUNT_IP, keys.keyHash(ACCOUNT_IP, EMAIL, CLIENT_IP), NOW, _) >> new AttemptCount(1, NOW)

        then:
        1 * transactionManager.commit(status)
        0 * status.setRollbackOnly()
        reservation.windows().keySet() as List == [IP, ACCOUNT, ACCOUNT_IP]
        reservation.windows().values()*.windowStart() == [NOW, NOW, NOW]
    }

    def "Freigeben in Sperrreihenfolge: IP und ACCOUNT minus 1, ACCOUNT_IP löschen, jeweils im reservierten Fenster"() {
        given:
        def limiter = limiter()
        def fenster = NOW - Duration.ofMinutes(3)
        repository.increment(_, _, _, _) >> new AttemptCount(2, fenster)
        def reservation = limiter.acquire(EMAIL, header(CLIENT_IP))

        when:
        limiter.recordSuccess(reservation)

        then:
        1 * repository.releaseOne(IP, keys.keyHash(IP, EMAIL, CLIENT_IP), fenster)

        then:
        1 * repository.releaseOne(ACCOUNT, keys.keyHash(ACCOUNT, EMAIL, CLIENT_IP), fenster)

        then:
        1 * repository.deleteIfWindow(ACCOUNT_IP, keys.keyHash(ACCOUNT_IP, EMAIL, CLIENT_IP), fenster)

        then:
        1 * transactionManager.commit(status)
        0 * repository._
    }

    def "Überschreitung markiert die Transaktion als Rollback und weist mit der längsten Restlaufzeit ab"() {
        given:
        def limiter = limiter()
        repository.increment(IP, _, _, _) >> new AttemptCount(31, NOW - Duration.ofMinutes(10))
        repository.increment(ACCOUNT, _, _, _) >> new AttemptCount(6, NOW - Duration.ofMinutes(12))
        repository.increment(ACCOUNT_IP, _, _, _) >> new AttemptCount(6, NOW - Duration.ofMinutes(5))

        when:
        limiter.acquire(EMAIL, header(CLIENT_IP))

        then:
        1 * status.setRollbackOnly()
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 600
        e.message == 'Too many login attempts'
    }

    def "Genau am Limit wird nicht abgewiesen"() {
        given:
        def limiter = limiter()
        repository.increment(IP, _, _, _) >> new AttemptCount(30, NOW)
        repository.increment(ACCOUNT, _, _, _) >> new AttemptCount(20, NOW)
        repository.increment(ACCOUNT_IP, _, _, _) >> new AttemptCount(5, NOW)

        when:
        def reservation = limiter.acquire(EMAIL, header(CLIENT_IP))

        then:
        0 * status.setRollbackOnly()
        !reservation.empty
    }

    def "Retry-After wird auf ganze Sekunden aufgerundet"() {
        given:
        def limiter = limiter()
        repository.increment(IP, _, _, _) >> new AttemptCount(1, NOW)
        repository.increment(ACCOUNT, _, _, _) >> new AttemptCount(1, NOW)
        repository.increment(ACCOUNT_IP, _, _, _) >> new AttemptCount(6, NOW - Duration.ofMinutes(14) - Duration.ofMillis(500))

        when:
        limiter.acquire(EMAIL, header(CLIENT_IP))

        then:
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 60
    }

    def "Modus OBSERVE: Überschreitung wird zurückgerollt, aber nicht abgewiesen, die Reservierung ist leer"() {
        given:
        def limiter = limiter(mode: 'OBSERVE')
        repository.increment(IP, _, _, _) >> new AttemptCount(1, NOW)
        repository.increment(ACCOUNT, _, _, _) >> new AttemptCount(6, NOW)
        repository.increment(ACCOUNT_IP, _, _, _) >> new AttemptCount(6, NOW)

        when:
        def reservation = limiter.acquire(EMAIL, header(CLIENT_IP))
        limiter.recordSuccess(reservation)

        then:
        1 * status.setRollbackOnly()
        reservation.empty
        reservation.ipPseudonym() == keys.ipPseudonym(CLIENT_IP)
        0 * repository.releaseOne(*_)
        0 * repository.deleteIfWindow(*_)
    }

    def "Aufräumen löscht in Batches, bis ein Batch kleiner als die Batchgröße ist, je Batch eine Transaktion"() {
        given:
        def limiter = limiter(batchSize: 2)

        when:
        limiter.deleteExpiredAttempts()

        then:
        3 * repository.deleteExpiredBatch(NOW - Duration.ofMinutes(15), 2) >>> [2, 2, 1]
        3 * transactionManager.commit(status)
    }

    private LoginRateLimiter limiter(Map args = [:]) {
        new LoginRateLimiter(repository, keys, transactionManager, Clock.fixed(JETZT, ZoneOffset.UTC),
                args.containsKey('mode') ? args.mode : 'ENFORCE', args.window ?: '15m',
                args.containsKey('accountIpLimit') ? args.accountIpLimit : 5,
                args.containsKey('accountLimit') ? args.accountLimit : 20,
                args.containsKey('ipLimit') ? args.ipLimit : 30,
                args.batchSize ?: 500)
    }

    /** Client-IP wie vom ClientIpResolver aus dem Edge-Header ermittelt. */
    private static ClientIp header(String ip) {
        new ClientIp(ip, ClientIp.Source.HEADER)
    }
}
