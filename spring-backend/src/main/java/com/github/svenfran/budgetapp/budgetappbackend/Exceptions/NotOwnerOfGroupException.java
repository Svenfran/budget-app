package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class NotOwnerOfGroupException extends Exception {
    public NotOwnerOfGroupException(String message) {
        super(message);
    }
}
