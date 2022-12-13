package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class SpendingsOverviewTotalYearDto {

    private Double sumTotalYear;
    private List<SpendingsOverviewUserDto> spendingsTotalUser;

    public SpendingsOverviewTotalYearDto(Double sumTotalYear, List<SpendingsOverviewUserDto> spendingsTotalUser) {
        this.sumTotalYear = sumTotalYear;
        this.spendingsTotalUser = spendingsTotalUser;
    }

    public SpendingsOverviewTotalYearDto() {
    }

    public Double getSumTotalYear() {
        return sumTotalYear;
    }

    public void setSumTotalYear(Double sumTotalYear) {
        this.sumTotalYear = sumTotalYear;
    }

    public List<SpendingsOverviewUserDto> getSpendingsTotalUser() {
        return spendingsTotalUser;
    }

    public void setSpendingsTotalUser(List<SpendingsOverviewUserDto> spendingsTotalUser) {
        this.spendingsTotalUser = spendingsTotalUser;
    }
}
