package com.github.svenfran.budgetapp.budgetappbackend.dto;

/**
 * Antwort und Notification (move-item) eines Moves: der verschobene Eintrag und die normalisierten Reihenfolgen
 * samt neuer Revision von Quell- und Zielliste. Enthält bewusst keine Eintragsdaten (Name, Erledigt-Status).
 */
public class ShoppingItemMovedDto {

    private Long groupId;
    private Long shoppingItemId;
    private ShoppingItemOrderDto source;
    private ShoppingItemOrderDto target;

    public ShoppingItemMovedDto(Long groupId, Long shoppingItemId, ShoppingItemOrderDto source, ShoppingItemOrderDto target) {
        this.groupId = groupId;
        this.shoppingItemId = shoppingItemId;
        this.source = source;
        this.target = target;
    }

    public ShoppingItemMovedDto() {
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

    public ShoppingItemOrderDto getSource() {
        return source;
    }

    public void setSource(ShoppingItemOrderDto source) {
        this.source = source;
    }

    public ShoppingItemOrderDto getTarget() {
        return target;
    }

    public void setTarget(ShoppingItemOrderDto target) {
        this.target = target;
    }
}
