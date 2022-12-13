package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class SpendingsOverviewPerMonthDto {

    private int month;
    private String monthName;
    private Double sumTotalMonth;
    private List<SpendingsOverviewUserDto> spendingsMonthlyUser;

    public SpendingsOverviewPerMonthDto(int month, String monthName, Double sumTotalMonth, List<SpendingsOverviewUserDto> spendingsMonthlyUser) {
        this.month = month;
        this.monthName = monthName;
        this.sumTotalMonth = sumTotalMonth;
        this.spendingsMonthlyUser = spendingsMonthlyUser;
    }

    public SpendingsOverviewPerMonthDto() {
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public String getMonthName() {
        return monthName;
    }

    public void setMonthName(String monthName) {
        this.monthName = monthName;
    }

    public Double getSumTotalMonth() {
        return sumTotalMonth;
    }

    public void setSumTotalMonth(Double sumTotalMonth) {
        this.sumTotalMonth = sumTotalMonth;
    }

    public List<SpendingsOverviewUserDto> getSpendingsMonthlyUser() {
        return spendingsMonthlyUser;
    }

    public void setSpendingsMonthlyUser(List<SpendingsOverviewUserDto> spendingsMonthlyUser) {
        this.spendingsMonthlyUser = spendingsMonthlyUser;
    }
}
