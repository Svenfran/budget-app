package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException
import com.github.svenfran.budgetapp.budgetappbackend.helper.LoginAttemptKeys
import com.github.svenfran.budgetapp.budgetappbackend.repository.LoginAttemptRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT_IP
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.IP

/**
 * SQL- und Fensterlogik des Login-Rate-Limits (DIV-64) gegen Postgres: Limits je Scope (5 Account+IP, 20 Account,
 * 30 IP je 15 Minuten), Ablauf der Wartezeit, fenstergebundene Freigabe, Retry-After, Aufräumen.
 *
 * Rollback-Spec: Alle Aufrufe nehmen an der Test-Transaktion teil. Eine Abweisung markiert die gemeinsame Transaktion
 * nur als Rollback, ihre Upserts bleiben im Feature sichtbar – deshalb prüft diese Spec nach einer Abweisung keine
 * Zählerstände (das tut die commit-abhängige LoginRateLimiterConcurrencyIntegrationSpec).
 *
 * IPs aus 198.51.100.0/24 und E-Mails mit Präfix "lrl-": Commit-Specs können Zeilen hinterlassen, deren Fenster bei
 * stehender testClock noch läuft; eigene Werte verhindern, dass sie hier mitzählen.
 */
class LoginRateLimiterIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiterIntegrationSpec)

    private static final Duration FENSTER = Duration.ofMinutes(15)

    @Autowired LoginRateLimiter rateLimiter
    @Autowired LoginAttemptKeys keys
    @Autowired LoginAttemptRepository repository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate

    def "Fünf Versuche auf Account+IP sind erlaubt, der sechste wird mit Retry-After 900 abgewiesen (AC2)"() {
        given:
        def email = 'lrl-ac2@example.org'
        def ip = '198.51.100.1'
        LOG.info('Szenario: Account lrl-ac2, IP {}, Uhr {}', ip, jetzt())

        when:
        def reservierungen = (1..5).collect { rateLimiter.acquire(email, ip) }
        def zaehler = zaehlerstaende(email, ip)
        LOG.info('5x acquire -> Zähler {}; 6. acquire, erwartet TooManyLoginAttemptsException', zaehler)
        rateLimiter.acquire(email, ip)

        then:
        reservierungen.every { !it.empty }
        zaehler == [IP: 5, ACCOUNT: 5, ACCOUNT_IP: 5]
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 900
    }

    def "Derselbe Account von anderen IPs ist bis zum Account-Limit 20 erlaubt, der 21. Versuch wird abgewiesen (AC4)"() {
        given:
        def email = 'lrl-verteilt@example.org'
        def ips = (1..5).collect { "198.51.100.1${it}".toString() }
        LOG.info('Szenario: Account lrl-verteilt, IPs {}; je 5 Versuche von den ersten 4 IPs', ips)

        when:
        ips.take(4).each { ip -> 5.times { rateLimiter.acquire(email, ip) } }
        def accountZaehler = zaehler(ACCOUNT, email, ips[0])
        LOG.info('20x acquire -> ACCOUNT {}; 21. Versuch von {}, erwartet TooManyLoginAttemptsException', accountZaehler, ips[4])
        rateLimiter.acquire(email, ips[4])

        then:
        accountZaehler == 20
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 900
    }

    def "Verschiedene Accounts von derselben IP sind bis zum IP-Limit 30 erlaubt, der 31. wird abgewiesen (AC4)"() {
        given:
        def ip = '198.51.100.20'
        def emails = (1..31).collect { "lrl-spray-${it}@example.org".toString() }
        LOG.info('Szenario: IP {}, 31 verschiedene Accounts lrl-spray-*', ip)

        when:
        emails.take(30).each { rateLimiter.acquire(it, ip) }
        def ipZaehler = zaehler(IP, emails[0], ip)
        LOG.info('30x acquire -> IP {}; 31. Account, erwartet TooManyLoginAttemptsException', ipZaehler)
        rateLimiter.acquire(emails[30], ip)

        then:
        ipZaehler == 30
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 900
    }

    def "Nach Ablauf des Fensters ist ein neuer Versuch ohne Entsperrung erlaubt und beginnt ein neues Fenster (AC3)"() {
        given:
        def email = 'lrl-ac3@example.org'
        def ip = '198.51.100.30'
        def start = jetzt()
        5.times { rateLimiter.acquire(email, ip) }
        def abweisung = abgewiesen { rateLimiter.acquire(email, ip) }
        LOG.info('Szenario: Account lrl-ac3, IP {}, 5 Versuche um {}, 6. -> {}', ip, start, abweisung?.class?.simpleName)

        when:
        testClock.advance(FENSTER)
        def reservierung = rateLimiter.acquire(email, ip)
        def fenster = fensterstarts(email, ip)
        def accountIpZaehler = zaehler(ACCOUNT_IP, email, ip)
        LOG.info('Uhr +15 min ({}) -> acquire ok, Fenster {}, ACCOUNT_IP {}', jetzt(), fenster, accountIpZaehler)

        then:
        abweisung instanceof TooManyLoginAttemptsException
        !reservierung.empty
        fenster == [IP: start + FENSTER, ACCOUNT: start + FENSTER, ACCOUNT_IP: start + FENSTER]
        accountIpZaehler == 1
    }

    def "Kurz vor Ablauf des Fensters wird weiter abgewiesen, mit Retry-After 1"() {
        given:
        def email = 'lrl-knapp@example.org'
        def ip = '198.51.100.31'
        5.times { rateLimiter.acquire(email, ip) }
        testClock.advance(FENSTER - Duration.ofMillis(1))
        LOG.info('Szenario: Account lrl-knapp, IP {}, 5 Versuche, Uhr 1 ms vor Fensterende ({})', ip, jetzt())

        when:
        LOG.info('acquire, erwartet TooManyLoginAttemptsException')
        rateLimiter.acquire(email, ip)

        then:
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 1
    }

    def "Erfolg gibt die eigene Reservierung frei: ACCOUNT_IP gelöscht, ACCOUNT und IP um genau 1 reduziert"() {
        given:
        def email = 'lrl-erfolg@example.org'
        def ip = '198.51.100.40'
        def reservierungen = (1..3).collect { rateLimiter.acquire(email, ip) }
        LOG.info('Szenario: Account lrl-erfolg, IP {}, 3 Versuche -> Zähler {}', ip, zaehlerstaende(email, ip))

        when:
        rateLimiter.recordSuccess(reservierungen.last())
        def zaehler = zaehlerstaende(email, ip)
        LOG.info('recordSuccess(3. Reservierung) -> Zähler {}', zaehler)

        then:
        zaehler == [IP: 2, ACCOUNT: 2, ACCOUNT_IP: null]
    }

    def "Freigabe einer Reservierung aus einem abgelaufenen Fenster ändert das neue Fenster nicht (Regression F-001)"() {
        given:
        def email = 'lrl-fensterwechsel@example.org'
        def ip = '198.51.100.41'
        def r1 = rateLimiter.acquire(email, ip)
        testClock.advance(FENSTER)
        rateLimiter.acquire(email, ip)
        def vorher = zaehlerstaende(email, ip)
        def fensterVorher = fensterstarts(email, ip)
        LOG.info('Szenario: R1 um {} reserviert, nach 15 min fremder Fehlversuch im neuen Fenster {} -> Zähler {}',
                r1.windows()[ACCOUNT_IP].windowStart(), fensterVorher, vorher)

        when:
        rateLimiter.recordSuccess(r1)
        def nachher = zaehlerstaende(email, ip)
        def fensterNachher = fensterstarts(email, ip)
        LOG.info('recordSuccess(R1) -> Zähler {}, Fenster {}', nachher, fensterNachher)

        then:
        vorher == [IP: 1, ACCOUNT: 1, ACCOUNT_IP: 1]
        nachher == vorher
        fensterNachher == fensterVorher
    }

    def "Zähler und Abweisung hängen nicht davon ab, ob der Account existiert (AC5)"() {
        given:
        def existierend = fixtures.user(email: 'lrl-existiert@example.org').email
        def unbekannt = 'lrl-unbekannt@example.org'
        LOG.info('Szenario: existierender Account lrl-existiert (IP .50), unbekannter Account lrl-unbekannt (IP .51), je 6 Versuche')

        when:
        def ergebnisExistierend = verlauf(existierend, '198.51.100.50')
        def ergebnisUnbekannt = verlauf(unbekannt, '198.51.100.51')
        LOG.info('existierend -> {}; unbekannt -> {}', ergebnisExistierend, ergebnisUnbekannt)

        then:
        ergebnisExistierend == ['ok'] * 5 + ['429 Retry-After 900']
        ergebnisUnbekannt == ergebnisExistierend
    }

    def "Retry-After entspricht bei unterschiedlich gestarteten Fenstern der längsten verbleibenden Sperre"() {
        given: 'IP-Fenster startet um T, Account+IP-Fenster um T+5 min'
        def ip = '198.51.100.60'
        def email = 'lrl-retry@example.org'
        def start = jetzt()
        (1..25).each { rateLimiter.acquire("lrl-retry-anderer-${it}@example.org", ip) }
        testClock.advance(Duration.ofMinutes(5))
        5.times { rateLimiter.acquire(email, ip) }
        def fenster = fensterstarts(email, ip)
        LOG.info('Szenario: IP {} mit 25 Fehlversuchen anderer Accounts ab {}, dann 5 Versuche lrl-retry -> Zähler {}, Fenster {}',
                ip, start, zaehlerstaende(email, ip), fenster)

        when: 'IP (bis T+15) und Account+IP (bis T+20) sind beide überschritten'
        def beideGesperrt = abgewiesen { rateLimiter.acquire(email, ip) }
        testClock.advance(Duration.ofMinutes(2))
        def zweiMinutenSpaeter = abgewiesen { rateLimiter.acquire(email, ip) }
        testClock.advance(Duration.ofMinutes(9))
        def nurAccountIp = abgewiesen { rateLimiter.acquire(email, ip) }
        LOG.info('T+5 -> {}, T+7 -> {}, T+16 (IP-Fenster abgelaufen) -> {}',
                beideGesperrt?.retryAfterSeconds, zweiMinutenSpaeter?.retryAfterSeconds, nurAccountIp?.retryAfterSeconds)

        then:
        fenster == [IP: start, ACCOUNT: start + Duration.ofMinutes(5), ACCOUNT_IP: start + Duration.ofMinutes(5)]
        beideGesperrt.retryAfterSeconds == 900
        zweiMinutenSpaeter.retryAfterSeconds == 780
        nurAccountIp.retryAfterSeconds == 240
    }

    def "Fenster-Kennung mit Sekundenbruchteilen entspricht dem gespeicherten window_start, die Freigabe trifft die Zeilen"() {
        given:
        def email = 'lrl-nanos@example.org'
        def ip = '198.51.100.70'
        testClock.advance(Duration.ofHours(10).plusNanos(123_456_789))
        rateLimiter.acquire(email, ip)
        LOG.info('Szenario: Account lrl-nanos, IP {}, Uhr {} (Nanosekunden), 1 Versuch', ip, jetzt())

        when:
        def reservierung = rateLimiter.acquire(email, ip)
        def reserviert = reservierung.windows().collectEntries { scope, w -> [scope.name(), w.windowStart()] }
        def gespeichert = fensterstarts(email, ip)
        rateLimiter.recordSuccess(reservierung)
        def zaehler = zaehlerstaende(email, ip)
        LOG.info('acquire -> reserviert {}, gespeichert {}; recordSuccess -> Zähler {}', reserviert, gespeichert, zaehler)

        then:
        reserviert == gespeichert
        zaehler == [IP: 1, ACCOUNT: 1, ACCOUNT_IP: null]
    }

    def "Aufräumen löscht nur abgelaufene Fenster, auch über mehrere Batches"() {
        given:
        def limiter = new LoginRateLimiter(repository, keys, transactionManager, testClock,
                'ENFORCE', '15m', 5, 20, 30, 2)
        def jetzt = jetzt()
        def abgelaufen = (1..4).collect { "lrl-cleanup-alt-$it".toString() }
        abgelaufen.each { zeileAnlegen(it, jetzt - Duration.ofMinutes(16)) }
        zeileAnlegen('lrl-cleanup-grenze', jetzt - FENSTER)
        def aktuell = ['lrl-cleanup-neu-1', 'lrl-cleanup-neu-2']
        aktuell.each { zeileAnlegen(it, jetzt - Duration.ofMinutes(14)) }
        LOG.info('Szenario: Uhr {}, 4 Zeilen 16 min alt, 1 genau 15 min, 2 Zeilen 14 min alt; Batchgröße 2', jetzt)

        when:
        limiter.deleteExpiredAttempts()
        def verbleibend = jdbcTemplate.queryForList(
                "select key_hash from login_attempt where key_hash like 'lrl-cleanup-%' order by key_hash", String)
        LOG.info('deleteExpiredAttempts() -> verbleibend {}', verbleibend)

        then:
        verbleibend == aktuell
    }

    /** Sechs Versuche; je Versuch "ok" oder "429 Retry-After <s>". */
    private List<String> verlauf(String email, String ip) {
        (1..6).collect {
            def e = abgewiesen { rateLimiter.acquire(email, ip) }
            e ? "429 Retry-After ${e.retryAfterSeconds}".toString() : 'ok'
        }
    }

    private static TooManyLoginAttemptsException abgewiesen(Closure aufruf) {
        try {
            aufruf.call()
            null
        } catch (TooManyLoginAttemptsException e) {
            e
        }
    }

    private LocalDateTime jetzt() {
        LocalDateTime.ofInstant(testClock.instant(), ZoneOffset.UTC)
    }

    private Integer zaehler(LoginAttemptScope scope, String email, String ip) {
        zeile(scope, email, ip)?.attempts as Integer
    }

    /** Zählerstand je Scope (null = keine Zeile). */
    private Map<String, Integer> zaehlerstaende(String email, String ip) {
        LoginAttemptScope.values().collectEntries { [it.name(), zaehler(it, email, ip)] }
    }

    /** window_start je Scope (null = keine Zeile). */
    private Map<String, LocalDateTime> fensterstarts(String email, String ip) {
        LoginAttemptScope.values().collectEntries { [it.name(), zeile(it, email, ip)?.windowStart] }
    }

    private Map zeile(LoginAttemptScope scope, String email, String ip) {
        def rows = jdbcTemplate.query('select attempts, window_start from login_attempt where scope = ? and key_hash = ?',
                { rs, n -> [attempts: rs.getInt('attempts'), windowStart: rs.getObject('window_start', LocalDateTime)] },
                scope.name(), keys.keyHash(scope, email, ip))
        rows ? rows[0] : null
    }

    private void zeileAnlegen(String keyHash, LocalDateTime windowStart) {
        jdbcTemplate.update('insert into login_attempt (scope, key_hash, window_start, attempts) values (?, ?, ?, 1)',
                IP.name(), keyHash, windowStart)
    }
}
