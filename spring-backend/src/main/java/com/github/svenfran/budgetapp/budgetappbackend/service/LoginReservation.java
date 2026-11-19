package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ergebnis von {@link LoginRateLimiter#acquire}: je Scope der Zählerschlüssel und das Fenster ({@code windowStart}),
 * in dem der Versuch gezählt wurde (DIV-64). Die Freigabe nach erfolgreichem Login ändert nur Zeilen, die noch in
 * genau diesem Fenster sind. Eine leere Reservierung (Modus {@code OFF}, Abweisung im Modus {@code OBSERVE}) gibt
 * nichts frei. Enthält keine Klartext-E-Mail oder -IP, nur Schlüssel-Hashes, Log-Pseudonyme und die IP-Quelle
 * ({@code header}/{@code remoteAddr}) für das Log.
 */
public record LoginReservation(String accountPseudonym, String ipPseudonym, String ipSource,
                               Map<LoginAttemptScope, ReservedWindow> windows) {

    /** Zählerschlüssel und Fenster-Kennung eines Scopes. */
    public record ReservedWindow(String keyHash, LocalDateTime windowStart) {
    }

    public LoginReservation {
        windows = windows.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new EnumMap<>(windows));
    }

    static LoginReservation empty(String accountPseudonym, String ipPseudonym, String ipSource) {
        return new LoginReservation(accountPseudonym, ipPseudonym, ipSource, Map.of());
    }

    public boolean isEmpty() {
        return windows.isEmpty();
    }
}
