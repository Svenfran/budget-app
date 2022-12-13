package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class ShoppingItemDoesNotBelongToShoppingListException extends Exception{
    public ShoppingItemDoesNotBelongToShoppingListException(String message) {
        super(message);
    }
}
