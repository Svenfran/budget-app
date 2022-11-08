package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;

public class ShoppingItemDto {

    private Long id;
    private String name;
    private boolean isCompleted;

    public ShoppingItemDto(ShoppingItem shoppingItem) {
        this.id = shoppingItem.getId();
        this.name = shoppingItem.getName();
        this.isCompleted = shoppingItem.isCompleted();
    }

    public ShoppingItemDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }
}
