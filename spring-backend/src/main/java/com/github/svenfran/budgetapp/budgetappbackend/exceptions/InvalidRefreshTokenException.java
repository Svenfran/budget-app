package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/**
 * Refresh abgelehnt (DIV-65): Token unbekannt, abgelaufen, widerrufen, bereits rotiert oder DeviceId falsch. Nach außen
 * immer derselbe Text und Code ({@code REFRESH_TOKEN_INVALID}, 401) – kein Orakel; der Grund steht nur im Log.
 */
public class InvalidRefreshTokenException extends Exception {
    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
