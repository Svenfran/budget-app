package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "group_details")
public class Group implements Serializable {

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

    @Column(name = "last_update_shoppinglist")
    private Date lastUpdateShoppingList;

    @OneToMany(mappedBy = "group")
    @JsonBackReference
    private Set<Cart> carts;

    @OneToMany(mappedBy = "group")
    @JsonBackReference
    private Set<ShoppingList> shoppingLists;

    @OneToMany(mappedBy = "group")
    @JsonBackReference
    private Set<Category> categories;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    @JsonBackReference
    private Set<User> members;

    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    @JsonBackReference
    private Set<User> memberList;

    public Group(Long id, String name, Date dateCreated, Date lastUpdate, Date lastUpdateShoppingList, Set<Cart> carts, Set<ShoppingList> shoppingLists, Set<Category> categories, User owner, Set<User> members, Set<User> memberList) {
        this.id = id;
        this.name = name;
        this.dateCreated = dateCreated;
        this.lastUpdate = lastUpdate;
        this.lastUpdateShoppingList = lastUpdateShoppingList;
        this.carts = carts;
        this.shoppingLists = shoppingLists;
        this.categories = categories;
        this.owner = owner;
        this.members = members;
        this.memberList = memberList;
    }

    public Group() {
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

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Set<User> getMembers() {
        return members;
    }

    public void setMembers(Set<User> members) {
        this.members = members;
    }

    public Set<Cart> getCarts() {
        return carts;
    }

    public void setCarts(Set<Cart> carts) {
        this.carts = carts;
    }

    public Set<ShoppingList> getShoppingLists() {
        return shoppingLists;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    public void setShoppingLists(Set<ShoppingList> shoppingLists) {
        this.shoppingLists = shoppingLists;
    }

    public Set<User> getMemberList() {
        return memberList;
    }

    public void setMemberList(Set<User> memberList) {
        this.memberList = memberList;
    }

    public void addMember(User member) {
        this.members.add(member);
        member.getGroups().add(this);
    }

    public void removeMember(User member) {
        this.members.remove(member);
        member.getGroups().remove(this);
    }

    public void removeAll(Set<User> members) {
        for (User member : members) removeMember(member);
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

    public Date getLastUpdateShoppingList() {
        return lastUpdateShoppingList;
    }

    public void setLastUpdateShoppingList(Date lastUpdateShoppingList) {
        this.lastUpdateShoppingList = lastUpdateShoppingList;
    }
}
