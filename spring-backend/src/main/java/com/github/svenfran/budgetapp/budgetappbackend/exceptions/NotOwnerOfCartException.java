package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class NotOwnerOfCartException extends Exception {
    public NotOwnerOfCartException(String message) {
        super(message);
    }
}
