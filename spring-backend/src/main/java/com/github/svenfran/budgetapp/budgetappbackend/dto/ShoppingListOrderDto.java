package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

/**
 * Reihenfolge der Einkaufslisten einer Gruppe. Im Request zählen nur groupId und shoppingListIds (revision wird
 * ignoriert); Antwort und Notification enthalten die normalisierte vollständige Reihenfolge und die neue Revision.
 */
public class ShoppingListOrderDto {

    private Long groupId;
    private long revision;
    private List<Long> shoppingListIds;

    public ShoppingListOrderDto(Long groupId, long revision, List<Long> shoppingListIds) {
        this.groupId = groupId;
        this.revision = revision;
        this.shoppingListIds = shoppingListIds;
    }

    public ShoppingListOrderDto() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public List<Long> getShoppingListIds() {
        return shoppingListIds;
    }

    public void setShoppingListIds(List<Long> shoppingListIds) {
        this.shoppingListIds = shoppingListIds;
    }
}
