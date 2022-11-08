package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;

public class AddEditShoppingListDto {

    private Long id;
    private String name;
    private Long groupId;

    public AddEditShoppingListDto(ShoppingList shoppingList) {
        this.id = shoppingList.getId();
        this.name = shoppingList.getName();
        this.groupId = shoppingList.getGroup().getId();
    }

    public AddEditShoppingListDto() {
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

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
