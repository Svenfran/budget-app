package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

/**
 * Verschiebt einen Eintrag in eine andere Liste derselben Gruppe. targetShoppingItemIds ist die gewünschte
 * Reihenfolge der Zielliste inklusive des verschobenen Eintrags (tolerant, siehe SortOrderMerger).
 */
public class MoveShoppingItemDto {

    private Long groupId;
    private Long shoppingItemId;
    private Long sourceShoppingListId;
    private Long targetShoppingListId;
    private List<Long> targetShoppingItemIds;

    public MoveShoppingItemDto(Long groupId, Long shoppingItemId, Long sourceShoppingListId, Long targetShoppingListId,
                               List<Long> targetShoppingItemIds) {
        this.groupId = groupId;
        this.shoppingItemId = shoppingItemId;
        this.sourceShoppingListId = sourceShoppingListId;
        this.targetShoppingListId = targetShoppingListId;
        this.targetShoppingItemIds = targetShoppingItemIds;
    }

    public MoveShoppingItemDto() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getShoppingItemId() {
        return shoppingItemId;
    }

    public void setShoppingItemId(Long shoppingItemId) {
        this.shoppingItemId = shoppingItemId;
    }

    public Long getSourceShoppingListId() {
        return sourceShoppingListId;
    }

    public void setSourceShoppingListId(Long sourceShoppingListId) {
        this.sourceShoppingListId = sourceShoppingListId;
    }

    public Long getTargetShoppingListId() {
        return targetShoppingListId;
    }

    public void setTargetShoppingListId(Long targetShoppingListId) {
        this.targetShoppingListId = targetShoppingListId;
    }

    public List<Long> getTargetShoppingItemIds() {
        return targetShoppingItemIds;
    }

    public void setTargetShoppingItemIds(List<Long> targetShoppingItemIds) {
        this.targetShoppingItemIds = targetShoppingItemIds;
    }
}
