package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewMonthlyTotalSumAmountDto {

    private int year;
    private int month;
    private Double sumAmountTotalPerMonth;

    public SpendingsOverviewMonthlyTotalSumAmountDto(int year, int month, Double sumAmountTotalPerMonth) {
        this.year = year;
        this.month = month;
        this.sumAmountTotalPerMonth = sumAmountTotalPerMonth;
    }

    public SpendingsOverviewMonthlyTotalSumAmountDto() {
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public Double getSumAmountTotalPerMonth() {
        return sumAmountTotalPerMonth;
    }

    public void setSumAmountTotalPerMonth(Double sumAmountTotalPerMonth) {
        this.sumAmountTotalPerMonth = sumAmountTotalPerMonth;
    }
}
