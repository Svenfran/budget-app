package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;

import java.util.Date;

public class GroupDto extends GroupSideNavDto{

    private Date dateCreated;
    /**
     * Nur Request (DIV-77): ausdrückliche Bestätigung eines Währungswechsels bei vorhandenen Buchungen. In Antworten
     * immer {@code null}.
     */
    private Boolean confirmCurrencyChange;

    public GroupDto(Group group) {
        super(group);
        this.dateCreated = group.getDateCreated();
    }

    public GroupDto() {
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Boolean getConfirmCurrencyChange() {
        return confirmCurrencyChange;
    }

    public void setConfirmCurrencyChange(Boolean confirmCurrencyChange) {
        this.confirmCurrencyChange = confirmCurrencyChange;
    }
}
