package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Zähler des Login-Rate-Limits (DIV-64) in der Tabelle {@code login_attempt}; native SQL, keine JPA-Entity.
 * Zeitpunkte sind UTC ({@code timestamp} ohne Zone). Transaktionen steuert der Aufrufer ({@code LoginRateLimiter}).
 */
@Repository
public class LoginAttemptRepository {

    /**
     * Zählt atomar hoch (Zeilensperre durch den Upsert): Ist das Fenster der Zeile abgelaufen
     * ({@code window_start <= threshold}), beginnt ein neues mit Zähler 1 und {@code window_start = now}.
     */
    private static final String INCREMENT = """
            INSERT INTO login_attempt (scope, key_hash, window_start, attempts)
            VALUES (:scope, :key, :now, 1)
            ON CONFLICT (scope, key_hash) DO UPDATE SET
              attempts     = CASE WHEN login_attempt.window_start <= :threshold THEN 1 ELSE login_attempt.attempts + 1 END,
              window_start = CASE WHEN login_attempt.window_start <= :threshold THEN :now ELSE login_attempt.window_start END
            RETURNING attempts, window_start
            """;

    private static final String RELEASE_ONE = """
            UPDATE login_attempt SET attempts = greatest(attempts - 1, 0)
            WHERE scope = :scope AND key_hash = :key AND window_start = :windowStart
            """;

    private static final String DELETE_IF_WINDOW = """
            DELETE FROM login_attempt
            WHERE scope = :scope AND key_hash = :key AND window_start = :windowStart
            """;

    /** Überspringt gesperrte Zeilen, wartet also nie auf eine Sperre (kann keinen Deadlock bilden). */
    private static final String DELETE_EXPIRED_BATCH = """
            DELETE FROM login_attempt
            WHERE (scope, key_hash) IN (
              SELECT scope, key_hash FROM login_attempt
              WHERE window_start <= :threshold
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LoginAttemptRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Zählerstand nach dem Hochzählen; {@code windowStart} ist der gespeicherte Wert (Fenster-Kennung). */
    public record AttemptCount(int attempts, LocalDateTime windowStart) {
    }

    public AttemptCount increment(LoginAttemptScope scope, String keyHash, LocalDateTime now, LocalDateTime threshold) {
        var params = key(scope, keyHash).addValue("now", now).addValue("threshold", threshold);
        return jdbc.queryForObject(INCREMENT, params,
                (rs, rowNum) -> new AttemptCount(rs.getInt("attempts"), rs.getObject("window_start", LocalDateTime.class)));
    }

    /** Gibt einen Versuch zurück, nur wenn die Zeile noch im Fenster {@code windowStart} ist. Liefert die Zeilenanzahl. */
    public int releaseOne(LoginAttemptScope scope, String keyHash, LocalDateTime windowStart) {
        return jdbc.update(RELEASE_ONE, key(scope, keyHash).addValue("windowStart", windowStart));
    }

    /** Löscht die Zeile, nur wenn sie noch im Fenster {@code windowStart} ist. Liefert die Zeilenanzahl. */
    public int deleteIfWindow(LoginAttemptScope scope, String keyHash, LocalDateTime windowStart) {
        return jdbc.update(DELETE_IF_WINDOW, key(scope, keyHash).addValue("windowStart", windowStart));
    }

    /** Löscht bis zu {@code batchSize} abgelaufene, nicht gesperrte Zeilen. Liefert die Anzahl. */
    public int deleteExpiredBatch(LocalDateTime threshold, int batchSize) {
        return jdbc.update(DELETE_EXPIRED_BATCH,
                new MapSqlParameterSource("threshold", threshold).addValue("batchSize", batchSize));
    }

    private static MapSqlParameterSource key(LoginAttemptScope scope, String keyHash) {
        return new MapSqlParameterSource("scope", scope.name()).addValue("key", keyHash);
    }
}
