package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class WrongPasswordException extends Exception {
    public WrongPasswordException(String message) {
        super(message);
    }
}
