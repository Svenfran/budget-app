package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.time.LocalDate;

public class StatisticsTimelineEntryDto {

    // MONTH: wochenbasiertes ISO-Jahr; YEAR/ALL: Kalenderjahr
    private int year;
    // nur YEAR
    private Integer month;
    // nur MONTH: ISO-Kalenderwoche, from/to auf den Monat beschnitten
    private Integer week;
    private LocalDate from;
    private LocalDate to;
    // von dir bezahlt, darf negativ sein
    private double amount;

    public StatisticsTimelineEntryDto(int year, Integer month, Integer week, LocalDate from, LocalDate to, double amount) {
        this.year = year;
        this.month = month;
        this.week = week;
        this.from = from;
        this.to = to;
        this.amount = amount;
    }

    public StatisticsTimelineEntryDto() {
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getWeek() {
        return week;
    }

    public void setWeek(Integer week) {
        this.week = week;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
