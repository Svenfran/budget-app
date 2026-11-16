package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

/** Settlement-Kategorie für einen normalen Einkauf (DIV-74, 400). */
public class SettlementCategoryNotAllowedException extends Exception {
    public SettlementCategoryNotAllowedException(String message) { super(message); }
}
