package com.github.svenfran.budgetapp.budgetappbackend.entity;

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "cart_template")
public class CartTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "recurrence_type")
    @Enumerated(EnumType.STRING)
    private RecurrenceType recurrenceType;

    @Column(name = "active")
    private boolean active;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "next_execution_date")
    private LocalDate nextExecutionDate;

    @Column(name = "end_date")
    private LocalDate endDate; // null = unbegrenzt

    public CartTemplate(Long id, Long userId, Long groupId, Long categoryId, String title, String description,
                        Double amount, RecurrenceType recurrenceType, boolean active, LocalDate startDate, LocalDate nextExecutionDate, LocalDate endDate) {
        this.id = id;
        this.userId = userId;
        this.groupId = groupId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.recurrenceType = recurrenceType;
        this.active = active;
        this.startDate = startDate;
        this.nextExecutionDate = nextExecutionDate;
        this.endDate = endDate;
    }

    public CartTemplate() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public RecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(RecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getNextExecutionDate() {
        return nextExecutionDate;
    }

    public void setNextExecutionDate(LocalDate nextExecutionDate) {
        this.nextExecutionDate = nextExecutionDate;
    }

    @Override
    public String toString() {
        return "CartTemplate{" +
                "id=" + id +
                ", userId=" + userId +
                ", groupId=" + groupId +
                ", categoryId=" + categoryId +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", recurrenceType=" + recurrenceType +
                ", active=" + active +
                ", startDate=" + startDate +
                ", nextExecutionDate=" + nextExecutionDate +
                ", endDate=" + endDate +
                '}';
    }
}
