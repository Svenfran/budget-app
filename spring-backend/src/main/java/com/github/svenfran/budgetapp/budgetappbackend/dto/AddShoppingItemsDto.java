package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class AddShoppingItemsDto {

    private Long groupId;
    private Long shoppingListId;
    private List<String> names;

    public AddShoppingItemsDto() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getShoppingListId() {
        return shoppingListId;
    }

    public void setShoppingListId(Long shoppingListId) {
        this.shoppingListId = shoppingListId;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }
}
