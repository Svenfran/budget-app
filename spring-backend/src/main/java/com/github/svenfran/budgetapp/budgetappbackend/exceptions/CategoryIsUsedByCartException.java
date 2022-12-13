package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class CategoryIsUsedByCartException extends Exception {
    public CategoryIsUsedByCartException(String message) {
        super(message);
    }
}
