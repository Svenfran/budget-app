package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Währungscode, der nicht zu den unterstützten Gruppenwährungen gehört (DIV-77) → 400. */
public class UnsupportedCurrencyException extends Exception {
    public UnsupportedCurrencyException(String message) { super(message); }
}
