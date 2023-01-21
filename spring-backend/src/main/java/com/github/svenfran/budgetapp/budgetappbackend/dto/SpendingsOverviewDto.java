package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class SpendingsOverviewDto {

    private int year;
    private Long groupId;
    private SpendingsOverviewTotalYearDto spendingsTotalYear;
    private List<SpendingsOverviewPerMonthDto> spendingsPerMonth;
    private List<SpendingsOverviewPerYearDto> spendingsPerYear;
    private List<Integer> availableYears;

    public SpendingsOverviewDto(int year, Long groupId, List<SpendingsOverviewPerMonthDto> spendingsPerMonth, List<Integer> availableYears) {
        this.year = year;
        this.groupId = groupId;
        this.spendingsPerMonth = spendingsPerMonth;
        this.availableYears = availableYears;
    }

    public SpendingsOverviewDto(int year, Long groupId, SpendingsOverviewTotalYearDto spendingsTotalYear, List<SpendingsOverviewPerYearDto> spendingsPerYear) {
        this.year = year;
        this.groupId = groupId;
        this.spendingsTotalYear = spendingsTotalYear;
        this.spendingsPerYear = spendingsPerYear;
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

    public List<SpendingsOverviewPerYearDto> getSpendingsPerYear() {
        return spendingsPerYear;
    }

    public void setSpendingsPerYear(List<SpendingsOverviewPerYearDto> spendingsPerYear) {
        this.spendingsPerYear = spendingsPerYear;
    }

    public List<Integer> getAvailableYears() {
        return availableYears;
    }

    public void setAvailableYears(List<Integer> availableYears) {
        this.availableYears = availableYears;
    }
}
