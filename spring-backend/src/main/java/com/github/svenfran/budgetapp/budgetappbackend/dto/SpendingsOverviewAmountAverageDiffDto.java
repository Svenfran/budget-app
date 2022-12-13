package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class SpendingsOverviewAmountAverageDiffDto {

    private Double sumAmount;
    private Double sumAveragePerMember;
    private Double diff;

    public SpendingsOverviewAmountAverageDiffDto(Double sumAmount, Double sumAveragePerMember, Double diff) {
        this.sumAmount = sumAmount;
        this.sumAveragePerMember = sumAveragePerMember;
        this.diff = diff;
    }

    public SpendingsOverviewAmountAverageDiffDto() {
    }

    public Double getSumAmount() {
        return sumAmount;
    }

    public void setSumAmount(Double sumAmount) {
        this.sumAmount = sumAmount;
    }

    public Double getSumAveragePerMember() {
        return sumAveragePerMember;
    }

    public void setSumAveragePerMember(Double sumAveragePerMember) {
        this.sumAveragePerMember = sumAveragePerMember;
    }

    public Double getDiff() {
        return diff;
    }

    public void setDiff(Double diff) {
        this.diff = diff;
    }
}
