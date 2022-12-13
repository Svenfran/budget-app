package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewUserDto {

    private Long userId;
    private String userName;
    private Double sum;
    private Double diff;

    public SpendingsOverviewUserDto(Long userId, String userName, Double sum, Double diff) {
        this.userId = userId;
        this.userName = userName;
        this.sum = sum;
        this.diff = diff;
    }

    public SpendingsOverviewUserDto() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Double getSum() {
        return sum;
    }

    public void setSum(Double sum) {
        this.sum = sum;
    }

    public Double getDiff() {
        return diff;
    }

    public void setDiff(Double diff) {
        this.diff = diff;
    }
}
