package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp;
import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope;
import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginRateLimitMode;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException;
import com.github.svenfran.budgetapp.budgetappbackend.helper.LoginAttemptKeys;
import com.github.svenfran.budgetapp.budgetappbackend.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Login-Rate-Limit (DIV-64): zählt jeden Login-Versuch vor der Passwortprüfung gegen drei Schlüssel
 * ({@link LoginAttemptScope}) mit festem Zeitfenster und weist ab, wenn ein Limit überschritten ist.
 *
 * <ul>
 *   <li><b>Reservieren</b> ({@link #acquire}): eine kurze Transaktion zählt alle drei Schlüssel per Upsert in
 *       Sperrreihenfolge hoch. Die Zeilensperren serialisieren parallele Versuche auf denselben Schlüssel, so kommen
 *       höchstens {@code limit} Versuche je Fenster durch. Wird ein Limit überschritten, wird die Transaktion
 *       zurückgerollt (abgewiesene Versuche zählen nicht und verlängern nichts).</li>
 *   <li><b>Freigeben</b> ({@link #recordSuccess}): nach erfolgreichem Login werden {@code IP}/{@code ACCOUNT} um den
 *       eigenen Versuch reduziert und {@code ACCOUNT_IP} gelöscht – nur, solange die Zeile noch im reservierten
 *       Fenster ist. Fremde Fehlversuche bleiben stehen.</li>
 *   <li><b>Aufräumen</b> ({@link #deleteExpiredAttempts}): löscht abgelaufene Fenster in Batches und überspringt
 *       gesperrte Zeilen ({@code SKIP LOCKED}), wartet also nie auf eine Sperre.</li>
 * </ul>
 *
 * Transaktionen über {@link TransactionTemplate} (Propagation {@code REQUIRED}) statt {@code @Transactional}: der
 * Rollback bei Abweisung geht ohne Exception, und der Limiter ist ohne AOP-Proxy testbar. Der Aufrufer ruft ihn
 * außerhalb einer Transaktion auf, damit jeder Schritt einzeln committet.
 *
 * <p>Betrieb: Der Zustand liegt in Postgres, übersteht Neustarts und gilt für alle Instanzen gemeinsam (Zeilensperren
 * und Sperrreihenfolge wirken DB-weit). Zeitbasis ist die {@link Clock} der Instanz in UTC; Uhrabweichungen zwischen
 * Instanzen verschieben Fenster höchstens um die Abweichung. Feste Fenster erlauben an der Fenstergrenze kurzzeitig
 * bis zu 2× Limit (bewusst, kein Sliding Window). Die Wartezeit endet automatisch mit dem Fenster.
 */
@Service
public class LoginRateLimiter {

    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiter.class);

    static final int DEFAULT_CLEANUP_BATCH_SIZE = 500;

    private final LoginAttemptRepository repository;
    private final LoginAttemptKeys keys;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final LoginRateLimitMode mode;
    private final Duration window;
    private final Map<LoginAttemptScope, Integer> limits;
    private final int cleanupBatchSize;

    @Autowired
    public LoginRateLimiter(LoginAttemptRepository repository, LoginAttemptKeys keys,
                            PlatformTransactionManager transactionManager, Clock clock,
                            @Value("${app.security.login-rate-limit.mode}") String mode,
                            @Value("${app.security.login-rate-limit.window}") String window,
                            @Value("${app.security.login-rate-limit.account-ip-limit}") int accountIpLimit,
                            @Value("${app.security.login-rate-limit.account-limit}") int accountLimit,
                            @Value("${app.security.login-rate-limit.ip-limit}") int ipLimit) {
        this(repository, keys, transactionManager, clock, mode, window, accountIpLimit, accountLimit, ipLimit,
                DEFAULT_CLEANUP_BATCH_SIZE);
    }

    /** Wie der Spring-Konstruktor, mit wählbarer Batchgröße des Aufräumens (Tests). */
    LoginRateLimiter(LoginAttemptRepository repository, LoginAttemptKeys keys,
                     PlatformTransactionManager transactionManager, Clock clock, String mode, String window,
                     int accountIpLimit, int accountLimit, int ipLimit, int cleanupBatchSize) {
        this.repository = repository;
        this.keys = keys;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.mode = parseMode(mode);
        this.window = parseWindow(window);
        this.limits = new EnumMap<>(Map.of(
                LoginAttemptScope.IP, requirePositive("ip-limit", ipLimit),
                LoginAttemptScope.ACCOUNT, requirePositive("account-limit", accountLimit),
                LoginAttemptScope.ACCOUNT_IP, requirePositive("account-ip-limit", accountIpLimit)));
        this.cleanupBatchSize = requirePositive("cleanup batch size", cleanupBatchSize);
    }

    /**
     * Zählt den Versuch für E-Mail und Client-IP (in Sperrreihenfolge) und liefert die Reservierung.
     * Bei Überschreitung wird nichts gezählt; im Modus {@code ENFORCE} folgt die Exception, im Modus {@code OBSERVE}
     * nur ein WARN-Log und eine leere Reservierung. Im Modus {@code OFF} wird nichts gezählt.
     */
    public LoginReservation acquire(String email, ClientIp client) throws TooManyLoginAttemptsException {
        var clientIp = client.address();
        var ipSource = client.source().logValue();
        var accountPseudonym = keys.accountPseudonym(email);
        var ipPseudonym = keys.ipPseudonym(clientIp);
        if (mode == LoginRateLimitMode.OFF) {
            return LoginReservation.empty(accountPseudonym, ipPseudonym, ipSource);
        }
        var now = now();
        var threshold = now.minus(window);

        var outcome = Objects.requireNonNull(transaction.execute(status -> {
            var windows = new EnumMap<LoginAttemptScope, LoginReservation.ReservedWindow>(LoginAttemptScope.class);
            var exceeded = new EnumMap<LoginAttemptScope, Long>(LoginAttemptScope.class);
            for (var scope : LoginAttemptScope.values()) {
                var keyHash = keys.keyHash(scope, email, clientIp);
                var count = repository.increment(scope, keyHash, now, threshold);
                windows.put(scope, new LoginReservation.ReservedWindow(keyHash, count.windowStart()));
                if (count.attempts() > limits.get(scope)) {
                    exceeded.put(scope, retryAfterSeconds(count.windowStart(), now));
                }
            }
            if (!exceeded.isEmpty()) {
                // Abgewiesene Versuche zählen nicht: alle Upserts dieser Transaktion zurückrollen
                status.setRollbackOnly();
            }
            return new Outcome(windows, exceeded);
        }));

        if (outcome.exceeded().isEmpty()) {
            return new LoginReservation(accountPseudonym, ipPseudonym, ipSource, outcome.windows());
        }
        var retryAfter = outcome.exceeded().values().stream().mapToLong(Long::longValue).max().orElseThrow();
        var scopes = outcome.exceeded().keySet().stream().map(Enum::name).collect(Collectors.joining(","));
        if (mode == LoginRateLimitMode.OBSERVE) {
            LOG.warn("event=login_rate_limit_would_block scope={} account={} ip={} ipSource={} retryAfter={}",
                    scopes, accountPseudonym, ipPseudonym, ipSource, retryAfter);
            return LoginReservation.empty(accountPseudonym, ipPseudonym, ipSource);
        }
        LOG.warn("event=login_rate_limited scope={} account={} ip={} ipSource={} retryAfter={}",
                scopes, accountPseudonym, ipPseudonym, ipSource, retryAfter);
        throw new TooManyLoginAttemptsException(retryAfter);
    }

    /**
     * Gibt die eigene Reservierung nach erfolgreichem Login frei (in Sperrreihenfolge): {@code IP}/{@code ACCOUNT}
     * minus 1, {@code ACCOUNT_IP} löschen – jeweils nur, wenn die Zeile noch im reservierten Fenster ist.
     */
    public void recordSuccess(LoginReservation reservation) {
        if (mode == LoginRateLimitMode.OFF || reservation.isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            for (var scope : LoginAttemptScope.values()) {
                var reserved = reservation.windows().get(scope);
                if (reserved == null) {
                    continue;
                }
                if (scope == LoginAttemptScope.ACCOUNT_IP) {
                    repository.deleteIfWindow(scope, reserved.keyHash(), reserved.windowStart());
                } else {
                    repository.releaseOne(scope, reserved.keyHash(), reserved.windowStart());
                }
            }
        });
    }

    /** Loggt einen fehlgeschlagenen Login (die Reservierung bleibt als gezählter Fehlversuch bestehen). */
    public void logFailure(LoginReservation reservation) {
        LOG.info("event=login_failed account={} ip={} ipSource={}",
                reservation.accountPseudonym(), reservation.ipPseudonym(), reservation.ipSource());
    }

    /** Löscht abgelaufene Fenster in Batches (je Batch eine eigene Transaktion), ohne auf Sperren zu warten. */
    @Scheduled(cron = "${task.clearLoginAttempts.schedule}")
    public void deleteExpiredAttempts() {
        var threshold = now().minus(window);
        int total = 0;
        int deleted;
        do {
            deleted = Objects.requireNonNull(transaction.execute(status -> repository.deleteExpiredBatch(threshold, cleanupBatchSize)));
            total += deleted;
        } while (deleted == cleanupBatchSize);
        if (total > 0) {
            LOG.info("event=login_attempts_cleanup deleted={}", total);
        }
    }

    private LocalDateTime now() {
        // UTC: keine Sprünge durch Sommer-/Winterzeit
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Restlaufzeit des Fensters in ganzen Sekunden (aufgerundet, mindestens 1). */
    private long retryAfterSeconds(LocalDateTime windowStart, LocalDateTime now) {
        var remainingNanos = Duration.between(now, windowStart.plus(window)).toNanos();
        var seconds = (remainingNanos + 999_999_999L) / 1_000_000_000L;
        return Math.max(1, seconds);
    }

    private record Outcome(Map<LoginAttemptScope, LoginReservation.ReservedWindow> windows,
                           Map<LoginAttemptScope, Long> exceeded) {
    }

    private static LoginRateLimitMode parseMode(String mode) {
        try {
            return LoginRateLimitMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("app.security.login-rate-limit.mode must be one of ENFORCE, OBSERVE, OFF but was '" + mode + "'");
        }
    }

    private static Duration parseWindow(String window) {
        Duration parsed;
        try {
            parsed = DurationStyle.detectAndParse(window);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("app.security.login-rate-limit.window is not a duration: '" + window + "'");
        }
        if (parsed.isNegative() || parsed.isZero()) {
            throw new IllegalStateException("app.security.login-rate-limit.window must be > 0 but was '" + window + "'");
        }
        return parsed;
    }

    private static int requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalStateException("login rate limit " + name + " must be >= 1 but was " + value);
        }
        return value;
    }
}
