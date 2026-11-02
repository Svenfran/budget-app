package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Predicate;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.toCents;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils.toLocalDate;

/**
 * Ausgabenauswertung der Statistikseite (DIV-63) in ganzen Cent, ohne Spring und Datenbank.
 * <p>
 * Definitionen wie in der Übersicht ({@code SpendingsOverviewService}): Gesamtausgaben = Summe aller Carts im Zeitraum,
 * Von dir bezahlt = Summe der Carts mit dem Nutzer als Zahler, jeweils je Cart auf Cent gerundet und inklusive
 * Ausgleichszahlungen und gelöschter Carts. Zeitreihe und Kategorien zeigen nur die persönlich bezahlten Carts; ihre
 * Summe ist jeweils gleich {@link StatisticsResult#paidCents()}. Bezahlte Einkäufe = Anzahl derselben Carts ohne
 * Ausgleichszahlungen (keine Einkäufe).
 */
public final class StatisticsCalculator {

    private static final Comparator<Cart> GROUP_THEN_ID = Comparator
            .comparing((Cart cart) -> cart.getGroup().getId())
            .thenComparing(Cart::getId);

    private static final Comparator<CategoryEntry> BY_AMOUNT_THEN_NAME = Comparator
            .comparingLong(CategoryEntry::amountCents).reversed()
            .thenComparing(CategoryEntry::name, String.CASE_INSENSITIVE_ORDER);

    private StatisticsCalculator() {
    }

    /**
     * @param carts           Carts der gewählten Gruppen (jede Gruppe höchstens einmal)
     * @param period          bei {@code MONTH} sind {@code year} und {@code month} Pflicht, bei {@code YEAR} nur {@code year}
     * @param availableYears  Jahre mit Carts in den gewählten Gruppen; bestimmt bei {@code ALL} den Jahresbereich
     * @param settlementNames Namen der Ausgleichszahlungs-Kategorie, normalisiert über {@link #normalize}
     */
    public static StatisticsResult calculate(Collection<Cart> carts, Long userId, StatisticsPeriod period,
                                             Integer year, Integer month, Collection<Integer> availableYears,
                                             Set<String> settlementNames) {
        Predicate<LocalDate> inPeriod = switch (period) {
            case MONTH -> date -> date.getYear() == year && date.getMonthValue() == month;
            case YEAR -> date -> date.getYear() == year;
            case ALL -> date -> true;
        };
        var periodCarts = carts.stream()
                .filter(cart -> inPeriod.test(toLocalDate(cart.getDatePurchased())))
                .sorted(GROUP_THEN_ID)
                .toList();
        var paidCarts = periodCarts.stream()
                .filter(cart -> cart.getUser().getId().equals(userId))
                .toList();

        long totalCents = periodCarts.stream().mapToLong(cart -> toCents(cart.getAmount())).sum();
        long paidCents = paidCarts.stream().mapToLong(cart -> toCents(cart.getAmount())).sum();
        long paidCartCount = paidCarts.stream()
                .filter(cart -> !settlementNames.contains(normalize(cart.getCategory().getName())))
                .count();
        var timeline = switch (period) {
            case MONTH -> weeklyTimeline(paidCarts, year, month);
            case YEAR -> monthlyTimeline(paidCarts, year);
            case ALL -> yearlyTimeline(paidCarts, availableYears);
        };
        return new StatisticsResult(totalCents, paidCents, paidCartCount, timeline, categories(paidCarts, settlementNames));
    }

    /** Vergleichsschlüssel für Kategorienamen: getrimmt, ohne Groß-/Kleinschreibung. */
    public static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /** ISO-8601-Kalenderwochen, die den Monat berühren; {@code from}/{@code to} sind auf den Monat beschnitten. */
    private static List<TimelineEntry> weeklyTimeline(List<Cart> paidCarts, int year, int month) {
        var firstDay = LocalDate.of(year, month, 1);
        var lastDay = firstDay.with(TemporalAdjusters.lastDayOfMonth());
        var entries = new ArrayList<TimelineEntry>();
        for (var monday = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
             !monday.isAfter(lastDay); monday = monday.plusWeeks(1)) {
            var from = monday.isBefore(firstDay) ? firstDay : monday;
            var sunday = monday.plusDays(6);
            var to = sunday.isAfter(lastDay) ? lastDay : sunday;
            long cents = sumCents(paidCarts, date -> !date.isBefore(from) && !date.isAfter(to));
            entries.add(new TimelineEntry(monday.get(IsoFields.WEEK_BASED_YEAR), null,
                    monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), from, to, cents));
        }
        return entries;
    }

    private static List<TimelineEntry> monthlyTimeline(List<Cart> paidCarts, int year) {
        var entries = new ArrayList<TimelineEntry>();
        for (int month = 1; month <= 12; month++) {
            int m = month;
            entries.add(new TimelineEntry(year, month, null, null, null,
                    sumCents(paidCarts, date -> date.getMonthValue() == m)));
        }
        return entries;
    }

    /** Ein Eintrag je Jahr vom ersten bis zum letzten Jahr mit Carts, Lücken mit 0; ohne Jahre leer. */
    private static List<TimelineEntry> yearlyTimeline(List<Cart> paidCarts, Collection<Integer> availableYears) {
        if (availableYears.isEmpty()) {
            return List.of();
        }
        int first = Collections.min(availableYears);
        int last = Collections.max(availableYears);
        var entries = new ArrayList<TimelineEntry>();
        for (int year = first; year <= last; year++) {
            int y = year;
            entries.add(new TimelineEntry(year, null, null, null, null,
                    sumCents(paidCarts, date -> date.getYear() == y)));
        }
        return entries;
    }

    /**
     * Netto je Kategoriename (normalisiert), alle Ausgleichszahlungs-Kategorien in einer Zeile. Angezeigt wird der
     * getrimmte Name der ersten Fundstelle (Carts nach Gruppen-ID, dann Cart-ID). Zeilen mit 0 entfallen.
     */
    private static List<CategoryEntry> categories(List<Cart> paidCarts, Set<String> settlementNames) {
        var rows = new LinkedHashMap<String, CategoryEntry>();
        for (var cart : paidCarts) {
            var name = cart.getCategory().getName();
            var key = normalize(name);
            boolean settlement = settlementNames.contains(key);
            long cents = toCents(cart.getAmount());
            rows.merge(settlement ? "\0settlement" : key,
                    new CategoryEntry(name == null ? "" : name.trim(), cents, settlement),
                    (row, add) -> new CategoryEntry(row.name(), row.amountCents() + add.amountCents(), row.settlement()));
        }
        return rows.values().stream()
                .filter(row -> row.amountCents() != 0)
                .sorted(BY_AMOUNT_THEN_NAME)
                .toList();
    }

    private static long sumCents(List<Cart> carts, Predicate<LocalDate> datePredicate) {
        return carts.stream()
                .filter(cart -> datePredicate.test(toLocalDate(cart.getDatePurchased())))
                .mapToLong(cart -> toCents(cart.getAmount()))
                .sum();
    }

    public record StatisticsResult(long totalCents, long paidCents, long paidCartCount, List<TimelineEntry> timeline,
                                   List<CategoryEntry> categories) {
    }

    /**
     * {@code year} ist bei {@code MONTH} das wochenbasierte ISO-Jahr, sonst das Kalenderjahr; {@code month} nur bei
     * {@code YEAR}; {@code week}, {@code from}, {@code to} nur bei {@code MONTH}.
     */
    public record TimelineEntry(int year, Integer month, Integer week, LocalDate from, LocalDate to, long amountCents) {
    }

    public record CategoryEntry(String name, long amountCents, boolean settlement) {
    }
}
