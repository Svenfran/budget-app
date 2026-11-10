package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Login abgewiesen, weil ein Rate-Limit überschritten ist (DIV-64); wird auf HTTP 429 mit {@code Retry-After} abgebildet. */
public class TooManyLoginAttemptsException extends Exception {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many login attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
