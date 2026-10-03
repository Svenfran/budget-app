package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class MemberAlreadyExistsException extends Exception {
    public MemberAlreadyExistsException(String message) {
        super(message);
    }
}
