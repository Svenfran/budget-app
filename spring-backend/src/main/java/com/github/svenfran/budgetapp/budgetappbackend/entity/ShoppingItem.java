package com.github.svenfran.budgetapp.budgetappbackend.entity;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "shoppingitem")
public class ShoppingItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "is_completed")
    private boolean isCompleted;

    @Column(name = "date_created", nullable = false, updatable = false)
    @CreationTimestamp
    private Date dateCreated;

    @Column(name = "last_update")
    @UpdateTimestamp
    private Date lastUpdate;

    // Liste ändert nur der Move per Update-Query (updatable = false): der Update-Pfad speichert per merge eine aus dem
    // DTO neu erzeugte Entity und würde sonst einen parallel committeten Move zurücksetzen
    @ManyToOne
    @JoinColumn(name = "shoppinglist_id", nullable = false, updatable = false)
    private ShoppingList shoppingList;

    // Position ändert nur der Reorder per Update-Query (updatable = false): der Update-Pfad speichert per merge eine
    // aus dem DTO neu erzeugte Entity und würde sonst die gespeicherte Position überschreiben
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    public ShoppingItem(Long id, String name, boolean isCompleted, Date dateCreated, Date lastUpdate, ShoppingList shoppingList) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
        this.dateCreated = dateCreated;
        this.lastUpdate = lastUpdate;
        this.shoppingList = shoppingList;
    }

    public ShoppingItem() {
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

    public ShoppingList getShoppingList() {
        return shoppingList;
    }

    public void setShoppingList(ShoppingList shoppingList) {
        this.shoppingList = shoppingList;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Date lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "ShoppingItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isCompleted=" + isCompleted +
                ", dateCreated=" + dateCreated +
                ", lastUpdate=" + lastUpdate +
                ", sortOrder=" + sortOrder +
                ", shoppingListId=" + (shoppingList == null ? null : shoppingList.getId()) +
                '}';
    }
}
