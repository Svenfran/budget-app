package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Einkauf einer Ausgleichszahlung über einen normalen Cart-Endpoint (DIV-74, 409). */
public class SettlementCartNotEditableException extends Exception {
    public SettlementCartNotEditableException(String message) { super(message); }
}
