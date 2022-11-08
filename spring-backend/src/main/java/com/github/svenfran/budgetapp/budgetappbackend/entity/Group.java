package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;

import javax.persistence.*;
import java.io.Serializable;
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

    @OneToMany(mappedBy = "group")
    @JsonBackReference
    private Set<Cart> carts;

    @OneToMany(mappedBy = "group")
    @JsonBackReference
    private Set<ShoppingList> shoppingLists;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    @JsonBackReference
    private Set<User> members;

    public Group(Long id, String name, Set<Cart> carts, User owner, Set<User> members, Set<ShoppingList> shoppingLists) {
        this.id = id;
        this.name = name;
        this.carts = carts;
        this.owner = owner;
        this.members = members;
        this.shoppingLists = shoppingLists;
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

    public void setShoppingLists(Set<ShoppingList> shoppingLists) {
        this.shoppingLists = shoppingLists;
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

}
