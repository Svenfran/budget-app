package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class ShoppingListDoesNotBelongToGroupException extends Exception{
    public ShoppingListDoesNotBelongToGroupException(String message) {
        super(message);
    }
}
