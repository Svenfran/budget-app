package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class NotOwnerOfCartException extends Exception {
    public NotOwnerOfCartException(String message) {
        super(message);
    }
}
