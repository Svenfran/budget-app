package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class UserIsNotAuthenticatedUser extends Exception{
    public UserIsNotAuthenticatedUser(String message) {
        super(message);
    }
}
