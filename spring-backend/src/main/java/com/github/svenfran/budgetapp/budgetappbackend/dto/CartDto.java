package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;

import java.util.Date;

public class CartDto {

    private Long id;
    private String title;
    private String description;
    private Double amount;
    private Date datePurchased;
    private Long groupId;
    private UserDto userDto;
    private CategoryDto categoryDto;

    public CartDto(Cart cart) {
        this.id = cart.getId();
        this.title = cart.getTitle();
        this.description = cart.getDescription();
        this.amount = cart.getAmount();
        this.datePurchased = cart.getDatePurchased();
        this.groupId = cart.getGroup().getId();
        this.userDto = new UserDto(cart.getUser());
        this.categoryDto = new CategoryDto(cart.getCategory());
    }

    public CartDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDatePurchased() {
        return datePurchased;
    }

    public void setDatePurchased(Date datePurchased) {
        this.datePurchased = datePurchased;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public void setUserDto(UserDto userDto) {
        this.userDto = userDto;
    }

    public CategoryDto getCategoryDto() {
        return categoryDto;
    }

    public void setCategoryDto(CategoryDto categoryDto) {
        this.categoryDto = categoryDto;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }
}
