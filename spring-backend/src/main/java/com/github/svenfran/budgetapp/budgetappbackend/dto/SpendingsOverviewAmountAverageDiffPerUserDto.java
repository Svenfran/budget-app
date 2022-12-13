package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewAmountAverageDiffPerUserDto extends SpendingsOverviewAmountAverageDiffDto {

    private Long userId;
    private int year;

    public SpendingsOverviewAmountAverageDiffPerUserDto(Double sumAmount, Double sumAveragePerMember, Double diff, Long userId, int year) {
        super(sumAmount, sumAveragePerMember, diff);
        this.userId = userId;
        this.year = year;
    }

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

}
