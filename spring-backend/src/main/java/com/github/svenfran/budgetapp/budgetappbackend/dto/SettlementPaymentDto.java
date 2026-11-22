package com.github.svenfran.budgetapp.budgetappbackend.dto;


import javax.validation.constraints.NotNull;
import java.time.LocalDate;

public class SettlementPaymentDto {

    // Settlement-Id, nur beim Bearbeiten (DIV-74). member ist immer der Empfänger; groupId wird beim Bearbeiten ignoriert.
    private Long id;
    // Zahler beim Anlegen (DIV-74, Richtungsumschalter): ohne Angabe der angemeldete Nutzer. Der angemeldete Nutzer muss
    // Zahler oder Empfänger sein. Beim Bearbeiten ignoriert (der Zahler ist nicht änderbar).
    private UserDto payer;
    private Double amount;
    @NotNull
    private Long groupId;
    private UserDto member;
    // Fachlicher Kalendertag (DIV-81), JSON "YYYY-MM-DD".
    private LocalDate datePurchased;

    public SettlementPaymentDto(Double amount, Long groupId, UserDto member, LocalDate datePurchased) {
        this.amount = amount;
        this.groupId = groupId;
        this.member = member;
        this.datePurchased = datePurchased;
    }

    public SettlementPaymentDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserDto getPayer() {
        return payer;
    }

    public void setPayer(UserDto payer) {
        this.payer = payer;
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

    public LocalDate getDatePurchased() {
        return datePurchased;
    }

    public void setDatePurchased(LocalDate datePurchased) {
        this.datePurchased = datePurchased;
    }
}
