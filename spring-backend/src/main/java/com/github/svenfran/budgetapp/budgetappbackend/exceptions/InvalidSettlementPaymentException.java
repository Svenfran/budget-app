package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Ungültige Ausgleichszahlung (DIV-74): fehlende Pflichtfelder, Betrag nicht > 0 bzw. nicht centgenau, Zahler = Empfänger. */
public class InvalidSettlementPaymentException extends Exception {
    public InvalidSettlementPaymentException(String message) { super(message); }
}
