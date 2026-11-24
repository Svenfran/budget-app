package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;

/**
 * Login abgewiesen, weil ein Rate-Limit überschritten ist (DIV-64); wird auf HTTP 429 mit {@code Retry-After} abgebildet.
 * Eigener Statushandler, daher liefert die Exception ihren Fehlercode selbst ({@link ApiErrorInfo}, DIV-108).
 */
public class TooManyLoginAttemptsException extends Exception implements ApiErrorInfo {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many login attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public ApiErrorCode getErrorCode() {
        return ApiErrorCode.TOO_MANY_LOGIN_ATTEMPTS;
    }
}
