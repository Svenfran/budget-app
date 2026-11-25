package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult.Reason
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Commit- und Nebenläufigkeitsverhalten der Refresh-Token-Sessions (DIV-65): Der Replay-Widerruf wird trotz
 * Ablehnung committet; parallele Refreshes mit demselben Token führen zu genau einer Rotation und einer
 * Kulanz-Ablehnung ohne Widerruf; Session-Erstellung und Widerruf aller Sessions serialisieren über die Nutzersperre.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; jeder Service-Aufruf ohne äußere Transaktion
 * committet einzeln. Überlappung wie in LoginRateLimiterConcurrencyIntegrationSpec: Transaktion 1 führt ihren Aufruf
 * aus und hält ihre Sperren offen, Transaktion 2 muss nachweislich auf eine Sperre warten (pg_stat_activity), erst dann
 * committet 1. Beim parallelen Refresh liest 2 also die Token-Ids, bevor 1 committet (F-002/F-003 des Plans).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSessionServiceCommitIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(AuthSessionServiceCommitIntegrationSpec)

    /** Sicherheitstimeout in Sekunden für Latches, das Warten auf die Sperre und die Threads. */
    private static final long TIMEOUT = 20

    private static final String DEVICE = 'device-1'

    @Autowired AuthSessionService service
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Replay-Widerruf ist nach der Ablehnung committet"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        service.refresh(issued.refreshToken(), DEVICE)
        testClock.advance(Duration.ofSeconds(61))
        LOG.info('Szenario: Nutzer {}, Session {} rotiert; Vorgänger 61 s später erneut verwendet', anna.id, issued.sessionId())

        when:
        def result = service.refresh(issued.refreshToken(), DEVICE)
        def stand = sessionStand(issued.sessionId())
        LOG.info('refresh(Vorgänger) -> {}; Session in neuer Abfrage {}', result, stand)

        then:
        result == new RefreshResult.Rejected(Reason.REUSE_DETECTED)
        stand.revoke_reason == 'REUSE_DETECTED'
        stand.revoked_at != null
    }

    def "Zwei parallele Refreshes mit demselben Token: genau eine Rotation, eine Kulanz-Ablehnung, Session bleibt aktiv"() {
        given:
        def anna = fixtures.user()
        def issued = service.createSession(anna, DEVICE)
        LOG.info('Szenario: Nutzer {}, Session {}; T1 rotiert und hält die Session-Sperre, T2 refresht denselben Token',
                anna.id, issued.sessionId())

        when:
        def (erster, zweiter) = overlapping({ service.refresh(issued.refreshToken(), DEVICE) },
                { service.refresh(issued.refreshToken(), DEVICE) })
        def stand = sessionStand(issued.sessionId())
        def aktuelle = aktuelleTokens(issued.sessionId())
        def danach = erster instanceof RefreshResult.Rotated
                ? service.refresh((erster as RefreshResult.Rotated).session().refreshToken(), DEVICE) : null
        LOG.info('T1 -> {}, T2 -> {}; Session widerrufen {}, aktuelle Tokens {}; Rotation des Ergebnisses von T1 -> {}',
                erster.class.simpleName, zweiter, stand.revoked_at != null, aktuelle, danach?.class?.simpleName)

        then:
        erster instanceof RefreshResult.Rotated
        zweiter == new RefreshResult.Rejected(Reason.REUSE_WITHIN_GRACE)
        stand.revoked_at == null
        aktuelle == 1
        danach instanceof RefreshResult.Rotated
    }

    def "createSession und revokeAllSessions überlappend (#erster zuerst): Endzustand #erwartet"() {
        given:
        def anna = fixtures.user()
        def bestehend = service.createSession(anna, 'device-alt')
        def aufrufe = [createSession    : { service.createSession(anna, DEVICE) },
                       revokeAllSessions: { service.revokeAllSessions(anna, SessionRevokeReason.PASSWORD_CHANGED) }]
        LOG.info('Szenario: Nutzer {} mit Session {}; T1 {} hält die Nutzersperre, T2 {}', anna.id, bestehend.sessionId(),
                erster, zweiter)

        when:
        overlapping(aufrufe[erster], aufrufe[zweiter])
        def aktiv = aktiveSessions(anna.id)
        def alle = sessionAnzahl(anna.id)
        LOG.info('-> Sessions gesamt {}, aktiv auf Geräten {}', alle, aktiv)

        then:
        alle == 2
        aktiv == aktivErwartet

        where:
        erster              | zweiter             || aktivErwartet | erwartet
        'createSession'     | 'revokeAllSessions' || []            | 'auch die neue Session widerrufen'
        'revokeAllSessions' | 'createSession'     || [DEVICE]      | 'nur die neue Session aktiv'
    }

    def "Zwei überlappende createSession auf demselben Gerät: genau eine aktive Session"() {
        given:
        def anna = fixtures.user()
        LOG.info('Szenario: Nutzer {}, T1 und T2 legen je eine Session auf {} an', anna.id, DEVICE)

        when:
        def (erste, zweite) = overlapping({ service.createSession(anna, DEVICE) }, { service.createSession(anna, DEVICE) })
        def aktiv = jdbcTemplate.queryForList(
                'select id from auth_session where user_id = ? and revoked_at is null', Long, anna.id)
        def relogin = jdbcTemplate.queryForList(
                "select id from auth_session where user_id = ? and revoke_reason = 'RELOGIN'", Long, anna.id)
        LOG.info('T1 -> Session {}, T2 -> Session {}; aktiv {}, RELOGIN {}', erste.sessionId(), zweite.sessionId(), aktiv, relogin)

        then:
        aktiv == [zweite.sessionId()]
        relogin == [erste.sessionId()]
    }

    /**
     * Führt {@code erster} in Transaktion 1 aus und hält sie offen, startet {@code zweiter} (eigene Transaktion), wartet,
     * bis dieser auf eine Sperre wartet, und committet dann 1. Liefert beide Ergebnisse.
     */
    private List overlapping(Closure erster, Closure zweiter) {
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                transaction.execute {
                    def ergebnis = erster.call()
                    ersterAusgefuehrt.countDown()
                    assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                    ergebnis
                }
            } as Callable)
            assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
            def t2 = pool.submit({ zweiter.call() } as Callable)
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

    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }

    private Map<String, Object> sessionStand(Long sessionId) {
        jdbcTemplate.queryForMap('select revoked_at, revoke_reason from auth_session where id = ?', sessionId)
    }

    private int aktuelleTokens(Long sessionId) {
        jdbcTemplate.queryForObject(
                'select count(*) from refresh_token where session_id = ? and rotated_at is null', Integer, sessionId)
    }

    private List<String> aktiveSessions(Long userId) {
        jdbcTemplate.queryForList(
                'select device_id from auth_session where user_id = ? and revoked_at is null order by id', String, userId)
    }

    private int sessionAnzahl(Long userId) {
        jdbcTemplate.queryForObject('select count(*) from auth_session where user_id = ?', Integer, userId)
    }
}
