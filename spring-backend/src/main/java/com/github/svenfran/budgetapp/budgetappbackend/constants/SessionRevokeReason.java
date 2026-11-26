package com.github.svenfran.budgetapp.budgetappbackend.constants;

/** Grund für den Widerruf einer {@code AuthSession} (DIV-65), gespeichert in {@code auth_session.revoke_reason}. */
public enum SessionRevokeReason {
    /** Abmeldung auf diesem Gerät. */
    LOGOUT,
    /** Erneute Anmeldung desselben Nutzers auf demselben Gerät. */
    RELOGIN,
    /** Ein bereits rotierter Refresh Token wurde außerhalb des Kulanzfensters erneut verwendet. */
    REUSE_DETECTED,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    EMAIL_CHANGED
}
