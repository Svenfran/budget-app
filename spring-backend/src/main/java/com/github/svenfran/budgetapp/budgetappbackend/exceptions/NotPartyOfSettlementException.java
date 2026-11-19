package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Angemeldeter Nutzer ist weder Zahler noch Empfänger der Ausgleichszahlung (DIV-74, 403). */
public class NotPartyOfSettlementException extends Exception {
    public NotPartyOfSettlementException(String message) { super(message); }
}
