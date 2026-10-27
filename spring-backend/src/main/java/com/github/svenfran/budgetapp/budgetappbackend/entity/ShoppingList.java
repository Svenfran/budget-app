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

    // Position und Revision ändert nur der Reorder per Update-Query (updatable = false): die Update-Pfade speichern
    // per merge eine aus dem DTO neu erzeugte Entity und würden sonst die gespeicherten Werte überschreiben
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "item_order_revision", nullable = false, updatable = false)
    private long itemOrderRevision;

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

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getItemOrderRevision() {
        return itemOrderRevision;
    }

    public void setItemOrderRevision(long itemOrderRevision) {
        this.itemOrderRevision = itemOrderRevision;
    }

    @Override
    public String toString() {
        return "ShoppingList{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", dateCreated=" + dateCreated +
                ", lastUpdate=" + lastUpdate +
                ", sortOrder=" + sortOrder +
                ", itemOrderRevision=" + itemOrderRevision +
                ", groupId=" + (group == null ? null : group.getId()) +
                '}';
    }
}
