package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingItem;
import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingList;

import java.util.Comparator;
import java.util.List;

public class ShoppingListDto {

    private Long id;
    private String name;
    private List<ShoppingItemDto> shoppingItems;
    private long listOrderRevision;
    private long itemOrderRevision;

    public ShoppingListDto(ShoppingList shoppingList, long listOrderRevision) {
        this.id = shoppingList.getId();
        this.name = shoppingList.getName();
        this.shoppingItems = shoppingList.getShoppingItems().stream()
                .sorted(Comparator.comparingInt(ShoppingItem::getSortOrder).thenComparing(ShoppingItem::getId))
                .map(ShoppingItemDto::new)
                .toList();
        this.listOrderRevision = listOrderRevision;
        this.itemOrderRevision = shoppingList.getItemOrderRevision();
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

    public long getListOrderRevision() {
        return listOrderRevision;
    }

    public void setListOrderRevision(long listOrderRevision) {
        this.listOrderRevision = listOrderRevision;
    }

    public long getItemOrderRevision() {
        return itemOrderRevision;
    }

    public void setItemOrderRevision(long itemOrderRevision) {
        this.itemOrderRevision = itemOrderRevision;
    }
}
