package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class NotOwnerOrMemberOfGroupException extends Exception {
    public NotOwnerOrMemberOfGroupException(String message) {
        super(message);
    }
}
