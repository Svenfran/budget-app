package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class CategoryIsUsedByCartException extends Exception {
    public CategoryIsUsedByCartException(String message) {
        super(message);
    }
}
