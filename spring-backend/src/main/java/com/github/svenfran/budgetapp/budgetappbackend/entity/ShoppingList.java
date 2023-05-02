package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "shoppinglist")
public class ShoppingList implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "date_created", nullable = false, updatable = false)
    @CreationTimestamp
    private Date dateCreated;

    @Column(name = "last_update")
    @UpdateTimestamp
    private Date lastUpdate;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @OneToMany(mappedBy = "shoppingList")
    @JsonBackReference
    private Set<ShoppingItem> shoppingItems;

    public ShoppingList(Long id, String name, Date dateCreated, Date lastUpdate, Group group, Set<ShoppingItem> shoppingItems) {
        this.id = id;
        this.name = name;
        this.dateCreated = dateCreated;
        this.lastUpdate = lastUpdate;
        this.group = group;
        this.shoppingItems = shoppingItems;
    }

    public ShoppingList() {
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

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Set<ShoppingItem> getShoppingItems() {
        return shoppingItems;
    }

    public void setShoppingItems(Set<ShoppingItem> shoppingItems) {
        this.shoppingItems = shoppingItems;
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
}
