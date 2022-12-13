package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class NotMemberOfGroupException extends Exception {
    public NotMemberOfGroupException(String message) {
        super(message);
    }
}
