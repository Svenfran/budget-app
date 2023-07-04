package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class UserAlreadyExistException extends Exception {
    public UserAlreadyExistException(String message) {
        super(message);
    }
}
