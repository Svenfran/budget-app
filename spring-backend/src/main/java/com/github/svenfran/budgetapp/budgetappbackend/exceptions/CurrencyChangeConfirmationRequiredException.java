package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/**
 * Währungswechsel einer Gruppe mit vorhandenen Buchungen ohne ausdrückliche Bestätigung (DIV-77) → 409. Das Frontend
 * erkennt den Fall am Status von {@code PUT /api/groups/update}, nicht am Text.
 */
public class CurrencyChangeConfirmationRequiredException extends Exception {
    public CurrencyChangeConfirmationRequiredException(String message) { super(message); }
}
