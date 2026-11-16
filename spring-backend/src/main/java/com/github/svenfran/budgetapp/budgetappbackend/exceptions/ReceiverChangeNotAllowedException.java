package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Nur der Zahler darf den Empfänger einer Ausgleichszahlung ändern (DIV-74, 403). */
public class ReceiverChangeNotAllowedException extends Exception {
    public ReceiverChangeNotAllowedException(String message) { super(message); }
}
