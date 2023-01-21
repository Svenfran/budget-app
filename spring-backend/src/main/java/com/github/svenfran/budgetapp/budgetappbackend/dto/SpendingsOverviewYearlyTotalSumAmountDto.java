package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewYearlyTotalSumAmountDto {

    private int year;
    private Double sumAmountTotalPerYear;

    public SpendingsOverviewYearlyTotalSumAmountDto(int year, Double sumAmountTotalPerYear) {
        this.year = year;
        this.sumAmountTotalPerYear = sumAmountTotalPerYear;
    }

    public SpendingsOverviewYearlyTotalSumAmountDto() {
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Double getSumAmountTotalPerYear() {
        return sumAmountTotalPerYear;
    }

    public void setSumAmountTotalPerYear(Double sumAmountTotalPerYear) {
        this.sumAmountTotalPerYear = sumAmountTotalPerYear;
    }
}
