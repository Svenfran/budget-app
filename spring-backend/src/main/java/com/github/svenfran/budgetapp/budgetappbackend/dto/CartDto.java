package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Date;

public class CartDto {

    private Long id;
    private String title;
    private String description;
    private Double amount;
    private Date datePurchased;
    @NotNull
    private Long groupId;
    private UserDto userDto;
    private CategoryDto categoryDto;
    private boolean deleted;
    private RecurrenceType recurrenceType = RecurrenceType.NONE;
    private boolean hasActiveTemplate;
    private LocalDate nextExecutionDate;
    private boolean templateUpdateSelected;
    private Long templateId;
    private boolean hasTemplateChanged = false;

    public CartDto(Cart cart) {
        this.id = cart.getId();
        this.title = cart.getTitle();
        this.description = cart.getDescription();
        this.amount = cart.getAmount();
        this.datePurchased = cart.getDatePurchased();
        this.groupId = cart.getGroup().getId();
        this.userDto = new UserDto(cart.getUser());
        this.categoryDto = new CategoryDto(cart.getCategory());
        this.deleted = cart.isDeleted();
        this.recurrenceType = getRecurrenceType(cart);
        this.hasActiveTemplate = getTemplateIsActive(cart);
        this.nextExecutionDate = getNextExecutionDate(cart);
        this.templateId = getTemplateId(cart);
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public RecurrenceType getRecurrenceType(Cart cart) {
        if (cart.getTemplate() != null && cart.getTemplate().isActive()) {
            return cart.getTemplate().getRecurrenceType();
        }
        return RecurrenceType.NONE;
    }

    public boolean getTemplateIsActive(Cart cart) {
        if (cart.getTemplate() != null) {
            return cart.getTemplate().isActive();
        }
        return false;
    }

    public LocalDate getNextExecutionDate(Cart cart) {
        if (cart.getTemplate() != null) {
            return cart.getTemplate().getNextExecutionDate();
        }
        return null;
    }

    public Long getTemplateId(Cart cart) {
        if (cart.getTemplate() != null) {
            return cart.getTemplate().getId();
        }
        return null;
    }

    public boolean isHasActiveTemplate() {
        return hasActiveTemplate;
    }

    public void setHasActiveTemplate(boolean hasActiveTemplate) {
        this.hasActiveTemplate = hasActiveTemplate;
    }

    public LocalDate getNextExecutionDate() {
        return nextExecutionDate;
    }

    public void setNextExecutionDate(LocalDate nextExecutionDate) {
        this.nextExecutionDate = nextExecutionDate;
    }

    public boolean isTemplateUpdateSelected() {
        return templateUpdateSelected;
    }

    public void setTemplateUpdateSelected(boolean templateUpdateSelected) {
        this.templateUpdateSelected = templateUpdateSelected;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public boolean isHasTemplateChanged() {
        return hasTemplateChanged;
    }

    public void setHasTemplateChanged(boolean hasTemplateChanged) {
        this.hasTemplateChanged = hasTemplateChanged;
    }
}
