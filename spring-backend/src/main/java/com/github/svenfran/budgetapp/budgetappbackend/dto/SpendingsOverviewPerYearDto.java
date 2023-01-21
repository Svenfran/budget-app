package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

public class SpendingsOverviewPerYearDto {

    private int year;
    private Double sumTotalYear;
    private List<SpendingsOverviewUserDto> spendingsYearlyUser;

    public SpendingsOverviewPerYearDto(int year, Double sumTotalYear, List<SpendingsOverviewUserDto> spendingsYearlyUser) {
        this.year = year;
        this.sumTotalYear = sumTotalYear;
        this.spendingsYearlyUser = spendingsYearlyUser;
    }

    public SpendingsOverviewPerYearDto() {
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }


    public Double getSumTotalYear() {
        return sumTotalYear;
    }

    public void setSumTotalYear(Double sumTotalYear) {
        this.sumTotalYear = sumTotalYear;
    }

    public List<SpendingsOverviewUserDto> getSpendingsYearlyUser() {
        return spendingsYearlyUser;
    }

    public void setSpendingsYearlyUser(List<SpendingsOverviewUserDto> spendingsYearlyUser) {
        this.spendingsYearlyUser = spendingsYearlyUser;
    }
}
