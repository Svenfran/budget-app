package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

public class CategoryBelongsNotToGroupException extends Exception {
    public CategoryBelongsNotToGroupException(String message) {
        super(message);
    }
}
