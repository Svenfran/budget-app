package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Cart-Betrag mit mehr als zwei Nachkommastellen (DIV-77) → 400. Ausgleichszahlungen: InvalidSettlementPaymentException. */
public class InvalidAmountException extends Exception {
    public InvalidAmountException(String message) { super(message); }
}
