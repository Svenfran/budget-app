package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod;

import java.util.List;

public class StatisticsDto {

    // null = alle Gruppen des Nutzers
    private Long groupId;
    /** Währung aller Beträge dieser Antwort (DIV-77): Gruppenwährung bzw. die gefilterte Währung bei „Alle Gruppen“. */
    private String currencyCode;
    /** Alle Währungen der Gruppen des Nutzers, unabhängig vom Filter (DIV-77, Auswahl im Frontend). */
    private List<String> availableCurrencies;
    private StatisticsPeriod period;
    private Integer year;
    private Integer month;
    private double totalSpent;
    private double paidByUser;
    // Carts mit dem Nutzer als Zahler im Zeitraum, ohne Ausgleichszahlungen
    private long paidCartCount;
    // Summe der offenen Salden der gewählten Gruppen, unabhängig vom Zeitraum
    private double currentBalance;
    // absteigend, Jahre mit Carts in den gewählten Gruppen
    private List<Integer> availableYears;
    // persönlich: nur Carts mit dem Nutzer als Zahler
    private List<StatisticsTimelineEntryDto> timeline;
    // persönlich; Betrag absteigend, ohne 0-Zeilen
    private List<StatisticsCategoryDto> categories;
    // Gruppenperspektive (DIV-72): alle Carts, unabhängig vom Zahler
    private StatisticsGroupViewDto groupView;

    public StatisticsDto() {
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public List<String> getAvailableCurrencies() {
        return availableCurrencies;
    }

    public void setAvailableCurrencies(List<String> availableCurrencies) {
        this.availableCurrencies = availableCurrencies;
    }

    public StatisticsPeriod getPeriod() {
        return period;
    }

    public void setPeriod(StatisticsPeriod period) {
        this.period = period;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public double getPaidByUser() {
        return paidByUser;
    }

    public void setPaidByUser(double paidByUser) {
        this.paidByUser = paidByUser;
    }

    public long getPaidCartCount() {
        return paidCartCount;
    }

    public void setPaidCartCount(long paidCartCount) {
        this.paidCartCount = paidCartCount;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(double currentBalance) {
        this.currentBalance = currentBalance;
    }

    public List<Integer> getAvailableYears() {
        return availableYears;
    }

    public void setAvailableYears(List<Integer> availableYears) {
        this.availableYears = availableYears;
    }

    public List<StatisticsTimelineEntryDto> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<StatisticsTimelineEntryDto> timeline) {
        this.timeline = timeline;
    }

    public List<StatisticsCategoryDto> getCategories() {
        return categories;
    }

    public void setCategories(List<StatisticsCategoryDto> categories) {
        this.categories = categories;
    }

    public StatisticsGroupViewDto getGroupView() {
        return groupView;
    }

    public void setGroupView(StatisticsGroupViewDto groupView) {
        this.groupView = groupView;
    }
}
