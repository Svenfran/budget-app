package com.github.svenfran.budgetapp.budgetappbackend.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Kaufereignis einer Einkaufsliste (DIV-23): ein erledigter Eintrag, der aus der Liste entfernt wurde. Gruppe und Liste
 * nur als IDs: die Historie überdauert das Löschen der Liste und wird erst mit der Gruppe gelöscht.
 */
@Entity
@Table(name = "shopping_purchase")
public class ShoppingPurchase implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "shopping_list_id", nullable = false)
    private Long shoppingListId;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "purchased_at", nullable = false)
    private Date purchasedAt;

    // ID des entfernten Eintrags; eindeutig, damit ein Kauf höchstens einmal erfasst wird
    @Column(name = "source_item_id", nullable = false, unique = true)
    private Long sourceItemId;

    public ShoppingPurchase() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Date getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Date purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public Long getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(Long sourceItemId) {
        this.sourceItemId = sourceItemId;
    }

    @Override
    public String toString() {
        return "ShoppingPurchase{" +
                "id=" + id +
                ", groupId=" + groupId +
                ", shoppingListId=" + shoppingListId +
                ", itemName='" + itemName + '\'' +
                ", purchasedAt=" + purchasedAt +
                ", sourceItemId=" + sourceItemId +
                '}';
    }
}
