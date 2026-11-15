package com.github.svenfran.budgetapp.budgetappbackend.dto;

import java.util.List;

/**
 * Gruppenperspektive der Statistikseite (DIV-72): alle Carts der gewählten Gruppen im Zeitraum, unabhängig vom Zahler.
 * Die Gesamtausgaben stehen in {@link StatisticsDto#getTotalSpent()}.
 */
public class StatisticsGroupViewDto {

    // Carts im Zeitraum ohne Ausgleichszahlungen
    private long cartCount;
    // Gesamtausgaben / cartCount, auf Cent gerundet (HALF_UP); 0 ohne Carts
    private double averagePerCart;
    // aktuelle Personen (Owner und Mitglieder) der gewählten Gruppen, jede einmal
    private int memberCount;
    // gleiche Einträge wie StatisticsDto.timeline, Beträge aller Zahler
    private List<StatisticsTimelineEntryDto> timeline;
    // Betrag absteigend, ohne 0-Zeilen
    private List<StatisticsCategoryDto> categories;

    public StatisticsGroupViewDto(long cartCount, double averagePerCart, int memberCount,
                                  List<StatisticsTimelineEntryDto> timeline, List<StatisticsCategoryDto> categories) {
        this.cartCount = cartCount;
        this.averagePerCart = averagePerCart;
        this.memberCount = memberCount;
        this.timeline = timeline;
        this.categories = categories;
    }

    public StatisticsGroupViewDto() {
    }

    public long getCartCount() {
        return cartCount;
    }

    public void setCartCount(long cartCount) {
        this.cartCount = cartCount;
    }

    public double getAveragePerCart() {
        return averagePerCart;
    }

    public void setAveragePerCart(double averagePerCart) {
        this.averagePerCart = averagePerCart;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
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
}
