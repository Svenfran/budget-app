package com.github.svenfran.budgetapp.budgetappbackend.service.auth;

import com.github.svenfran.budgetapp.budgetappbackend.service.AuthSessionService;

/**
 * Ergebnis von {@link AuthSessionService#refresh} (DIV-65). Der Service wirft bei Ablehnung keine Exception, damit ein
 * Session-Widerruf (Replay) sicher committet wird; erst der Aufrufer außerhalb der Transaktion übersetzt
 * {@link Rejected} in eine HTTP-Antwort.
 */
public sealed interface RefreshResult {

    /** Token rotiert: neuer Refresh Token in {@code session}. */
    record Rotated(IssuedSession session) implements RefreshResult {
    }

    /** Abgelehnt; der Grund ist nur für Logs und Tests, nach außen gibt es eine einheitliche Antwort. */
    record Rejected(Reason reason) implements RefreshResult {
    }

    enum Reason {
        /** Token unbekannt (nie ausgestellt oder Session gelöscht). */
        UNKNOWN_TOKEN,
        SESSION_REVOKED,
        /** Absolute Höchstdauer der Session erreicht. */
        SESSION_EXPIRED,
        /** DeviceId passt nicht zur Session (kein Widerruf). */
        DEVICE_MISMATCH,
        /** Nutzer gelöscht (anonymisiert). */
        USER_DELETED,
        /** Gerade rotierter Token im Kulanzfenster, Nachfolger unbenutzt (kein Widerruf). */
        REUSE_WITHIN_GRACE,
        /** Wiederverwendung eines rotierten Tokens: Session widerrufen. */
        REUSE_DETECTED,
        /** Aktueller Token nach Leerlauf abgelaufen. */
        TOKEN_EXPIRED
    }
}
