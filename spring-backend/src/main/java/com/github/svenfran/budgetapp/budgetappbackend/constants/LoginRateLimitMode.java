package com.github.svenfran.budgetapp.budgetappbackend.constants;

/** Betriebsmodus des Login-Rate-Limits (DIV-64), Property {@code app.security.login-rate-limit.mode}. */
public enum LoginRateLimitMode {
    /** Zählen und bei Überschreitung mit 429 abweisen. */
    ENFORCE,
    /** Zählen und Überschreitungen nur loggen ({@code event=login_rate_limit_would_block}), kein 429. */
    OBSERVE,
    /** Notfallschalter: kein Zählen, keine Abweisung. */
    OFF
}
