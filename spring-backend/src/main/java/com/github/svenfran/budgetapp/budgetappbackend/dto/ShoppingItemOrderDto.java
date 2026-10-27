package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

/**
 * Reihenfolge der Einträge einer Einkaufsliste. Im Request zählen nur groupId, shoppingListId und shoppingItemIds
 * (revision wird ignoriert); Antwort und Notification enthalten die normalisierte vollständige Reihenfolge und die
 * neue Revision der Liste.
 */
public class ShoppingItemOrderDto {

    private Long groupId;
    private Long shoppingListId;
    private long revision;
    private List<Long> shoppingItemIds;

    public ShoppingItemOrderDto(Long groupId, Long shoppingListId, long revision, List<Long> shoppingItemIds) {
        this.groupId = groupId;
        this.shoppingListId = shoppingListId;
        this.revision = revision;
        this.shoppingItemIds = shoppingItemIds;
    }

    public ShoppingItemOrderDto() {
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

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public List<Long> getShoppingItemIds() {
        return shoppingItemIds;
    }

    public void setShoppingItemIds(List<Long> shoppingItemIds) {
        this.shoppingItemIds = shoppingItemIds;
    }
}
