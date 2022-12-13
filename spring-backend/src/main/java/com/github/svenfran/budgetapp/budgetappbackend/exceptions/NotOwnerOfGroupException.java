package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class NotOwnerOfGroupException extends Exception {
    public NotOwnerOfGroupException(String message) {
        super(message);
    }
}
