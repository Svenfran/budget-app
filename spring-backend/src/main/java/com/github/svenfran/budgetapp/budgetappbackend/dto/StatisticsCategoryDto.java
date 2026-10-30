package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class StatisticsCategoryDto {

    // angezeigter Name; bei settlement = true übersetzt das Frontend
    private String name;
    // netto, darf negativ sein
    private double amount;
    private boolean settlement;

    public StatisticsCategoryDto(String name, double amount, boolean settlement) {
        this.name = name;
        this.amount = amount;
        this.settlement = settlement;
    }

    public StatisticsCategoryDto() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public boolean isSettlement() {
        return settlement;
    }

    public void setSettlement(boolean settlement) {
        this.settlement = settlement;
    }
}
