package com.github.svenfran.budgetapp.budgetappbackend.entity;

import javax.persistence.*;
import java.io.Serializable;

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

    @ManyToOne
    @JoinColumn(name = "shoppinglist_id", nullable = false)
    private ShoppingList shoppingList;

    public ShoppingItem(Long id, String name, boolean isCompleted, ShoppingList shoppingList) {
        this.id = id;
        this.name = name;
        this.isCompleted = isCompleted;
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
}
