package com.github.svenfran.budgetapp.budgetappbackend.dto;


import javax.validation.constraints.NotNull;
import java.util.Date;

public class SettlementPaymentDto {

    private Double amount;
    @NotNull
    private Long groupId;
    private UserDto member;
    private Date datePurchased;

    public SettlementPaymentDto(Double amount, Long groupId, UserDto member, Date datePurchased) {
        this.amount = amount;
        this.groupId = groupId;
        this.member = member;
        this.datePurchased = datePurchased;
    }

    public SettlementPaymentDto() {
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public UserDto getMember() {
        return member;
    }

    public void setMember(UserDto member) {
        this.member = member;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Date getDatePurchased() {
        return datePurchased;
    }

    public void setDatePurchased(Date datePurchased) {
        this.datePurchased = datePurchased;
    }
}
