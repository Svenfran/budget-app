package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;

public class AddEditShoppingItemDto {

    private Long id;
    private String name;
    private boolean isCompleted;
    private Long shoppingListId;
    private Long groupId;

    public AddEditShoppingItemDto(ShoppingItem shoppingItem) {
        this.id = shoppingItem.getId();
        this.name = shoppingItem.getName();
        this.isCompleted = shoppingItem.isCompleted();
        this.shoppingListId = shoppingItem.getShoppingList().getId();
        this.groupId = shoppingItem.getShoppingList().getGroup().getId();
    }

    public AddEditShoppingItemDto() {
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

    public Long getShoppingListId() {
        return shoppingListId;
    }

    public void setShoppingListId(Long shoppingListId) {
        this.shoppingListId = shoppingListId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
