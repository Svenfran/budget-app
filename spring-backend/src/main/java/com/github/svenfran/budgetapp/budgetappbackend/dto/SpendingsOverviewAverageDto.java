package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewAverageDto {

    private Long userId;
    private Double averagePerMember;
    private int month;
    private int year;

    public SpendingsOverviewAverageDto(Long userId, Double averagePerMember, int month, int year) {
        this.userId = userId;
        this.averagePerMember = averagePerMember;
        this.month = month;
        this.year = year;
    }

    public SpendingsOverviewAverageDto(Long userId, Double averagePerMember, int year) {
        this.userId = userId;
        this.averagePerMember = averagePerMember;
        this.year = year;
    }

    public SpendingsOverviewAverageDto() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getAveragePerMember() {
        return averagePerMember;
    }

    public void setAveragePerMember(Double averagePerMember) {
        this.averagePerMember = averagePerMember;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }
}
