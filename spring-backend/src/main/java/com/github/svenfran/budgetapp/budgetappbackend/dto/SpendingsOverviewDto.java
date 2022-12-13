package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class SpendingsOverviewDto {

    private int year;
    private Long groupId;
    private SpendingsOverviewTotalYearDto spendingsTotalYear;
    private List<SpendingsOverviewPerMonthDto> spendingsPerMonth;

    public SpendingsOverviewDto(int year, Long groupId, SpendingsOverviewTotalYearDto spendingsTotalYear, List<SpendingsOverviewPerMonthDto> spendingsPerMonth) {
        this.year = year;
        this.groupId = groupId;
        this.spendingsTotalYear = spendingsTotalYear;
        this.spendingsPerMonth = spendingsPerMonth;
    }

    public SpendingsOverviewDto() {
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public SpendingsOverviewTotalYearDto getSpendingsTotalYear() {
        return spendingsTotalYear;
    }

    public void setSpendingsTotalYear(SpendingsOverviewTotalYearDto spendingsTotalYear) {
        this.spendingsTotalYear = spendingsTotalYear;
    }

    public List<SpendingsOverviewPerMonthDto> getSpendingsPerMonth() {
        return spendingsPerMonth;
    }

    public void setSpendingsPerMonth(List<SpendingsOverviewPerMonthDto> spendingsPerMonth) {
        this.spendingsPerMonth = spendingsPerMonth;
    }
}
