package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SettlementPaymentDto {

    private Double amount;
    private Long groupId;
    private UserDto member;

    public SettlementPaymentDto(Double amount, Long groupId, UserDto member) {
        this.amount = amount;
        this.groupId = groupId;
        this.member = member;
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
}
