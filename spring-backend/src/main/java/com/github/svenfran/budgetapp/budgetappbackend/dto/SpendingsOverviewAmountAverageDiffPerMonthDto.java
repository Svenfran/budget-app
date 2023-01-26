package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewAmountAverageDiffPerMonthDto extends SpendingsOverviewAmountAverageDiffDto {

    private Long userId;
    private int year;
    private int month;

    public SpendingsOverviewAmountAverageDiffPerMonthDto(Double sumAmount, Double sumAveragePerMember, Double diff, Long userId, int year, int month) {
        super(sumAmount, sumAveragePerMember, diff);
        this.userId = userId;
        this.year = year;
        this.month = month;
    }

    public SpendingsOverviewAmountAverageDiffPerMonthDto() {}

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
}
