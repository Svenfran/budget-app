package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Ausgleichszahlung existiert nicht (mehr) (DIV-74, 404). */
public class SettlementNotFoundException extends Exception {
    public SettlementNotFoundException(String message) { super(message); }
}
