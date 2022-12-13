package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class UserNotFoundException extends Exception {
    public UserNotFoundException(String message) {
        super(message);
    }
}
