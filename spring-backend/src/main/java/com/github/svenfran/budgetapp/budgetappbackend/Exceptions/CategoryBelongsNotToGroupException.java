package com.github.svenfran.budgetapp.budgetappbackend.Exceptions;

public class CategoryBelongsNotToGroupException extends Exception {
    public CategoryBelongsNotToGroupException(String message) {
        super(message);
    }
}
