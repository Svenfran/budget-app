package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class ShoppingItemDoesNotBelongToShoppingListException extends Exception{
    public ShoppingItemDoesNotBelongToShoppingListException(String message) {
        super(message);
    }
}
