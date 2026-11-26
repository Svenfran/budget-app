package com.github.svenfran.budgetapp.budgetappbackend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lebensdauern der Anmeldung (DIV-65), Properties {@code app.auth.*} im Format {@code 15m}, {@code 30d}, {@code 60s}.
 *
 * <ul>
 *   <li>{@code accessTokenValidity} – Gültigkeit des Access Tokens (JWT).</li>
 *   <li>{@code refreshTokenValidity} – Leerlauf-Gültigkeit eines Refresh Tokens; jede Rotation beginnt sie neu
 *       (Sliding), höchstens bis zur absoluten Grenze der Session.</li>
 *   <li>{@code sessionMaxLifetime} – absolute Höchstdauer einer Session ab Login; Rotation verlängert sie nicht.</li>
 *   <li>{@code refreshReuseGrace} – Kulanzfenster: Ein gerade rotierter Token, dessen Nachfolger noch unbenutzt ist,
 *       wird innerhalb dieses Fensters ohne Session-Widerruf abgelehnt (paralleler Duplikat-Request), danach gilt die
 *       Wiederverwendung als Replay.</li>
 * </ul>
 */
@Component
public class AuthProperties {

    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;
    private final Duration sessionMaxLifetime;
    private final Duration refreshReuseGrace;

    @Autowired
    public AuthProperties(@Value("${app.auth.access-token-validity}") String accessTokenValidity,
                          @Value("${app.auth.refresh-token-validity}") String refreshTokenValidity,
                          @Value("${app.auth.session-max-lifetime}") String sessionMaxLifetime,
                          @Value("${app.auth.refresh-reuse-grace}") String refreshReuseGrace) {
        this(parse("access-token-validity", accessTokenValidity),
                parse("refresh-token-validity", refreshTokenValidity),
                parse("session-max-lifetime", sessionMaxLifetime),
                parse("refresh-reuse-grace", refreshReuseGrace));
    }

    public AuthProperties(Duration accessTokenValidity, Duration refreshTokenValidity, Duration sessionMaxLifetime,
                          Duration refreshReuseGrace) {
        this.accessTokenValidity = requirePositive("access-token-validity", accessTokenValidity);
        this.refreshTokenValidity = requirePositive("refresh-token-validity", refreshTokenValidity);
        this.sessionMaxLifetime = requirePositive("session-max-lifetime", sessionMaxLifetime);
        if (refreshReuseGrace == null || refreshReuseGrace.isNegative()) {
            throw new IllegalStateException("app.auth.refresh-reuse-grace must not be negative");
        }
        this.refreshReuseGrace = refreshReuseGrace;
    }

    public Duration getAccessTokenValidity() {
        return accessTokenValidity;
    }

    public Duration getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    public Duration getSessionMaxLifetime() {
        return sessionMaxLifetime;
    }

    public Duration getRefreshReuseGrace() {
        return refreshReuseGrace;
    }

    private static Duration parse(String name, String value) {
        try {
            return DurationStyle.detectAndParse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.auth." + name + " is not a valid duration", e);
        }
    }

    private static Duration requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("app.auth." + name + " must be positive");
        }
        return value;
    }
}
