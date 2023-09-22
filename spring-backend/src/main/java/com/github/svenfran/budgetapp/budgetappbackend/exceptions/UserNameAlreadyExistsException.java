package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class UserNameAlreadyExistsException extends Exception {
    public UserNameAlreadyExistsException(String message) {
        super(message);
    }
}
