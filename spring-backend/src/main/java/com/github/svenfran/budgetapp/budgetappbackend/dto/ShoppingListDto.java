package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;

import java.util.List;

public class ShoppingListDto {

    private Long id;
    private String name;
    private List<ShoppingItemDto> shoppingItems;

    public ShoppingListDto(ShoppingList shoppingList) {
        this.id = shoppingList.getId();
        this.name = shoppingList.getName();
        this.shoppingItems = shoppingList.getShoppingItems().stream().map(ShoppingItemDto::new).sorted((a,b) -> (int) (a.getId() - b.getId())).toList();
    }

    public ShoppingListDto() {
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

    public List<ShoppingItemDto> getShoppingItems() {
        return shoppingItems;
    }

    public void setShoppingItems(List<ShoppingItemDto> shoppingItems) {
        this.shoppingItems = shoppingItems;
    }
}
