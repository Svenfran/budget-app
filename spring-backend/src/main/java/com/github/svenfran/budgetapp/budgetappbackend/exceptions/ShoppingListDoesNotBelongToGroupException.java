package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class ShoppingListDoesNotBelongToGroupException extends Exception{
    public ShoppingListDoesNotBelongToGroupException(String message) {
        super(message);
    }
}
