package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class NotOwnerOrMemberOfGroupException extends Exception {
    public NotOwnerOrMemberOfGroupException(String message) {
        super(message);
    }
}
