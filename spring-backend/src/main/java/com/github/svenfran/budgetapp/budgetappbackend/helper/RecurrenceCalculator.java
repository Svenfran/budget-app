package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Termine wiederkehrender Einkäufe (DIV-25): Monatliche und jährliche Folgetermine werden aus dem Ankertag des
 * Templates gebildet und nur in Monaten ohne diesen Tag auf das Monatsende gekappt (31.01. → 28.02. → 31.03.).
 * Ein Weiterrechnen vom gekappten Datum würde dauerhaft auf den früheren Tag wandern.
 */
public final class RecurrenceCalculator {

    private RecurrenceCalculator() {
    }

    /** Genau ein Wiederholungsschritt. Bei DAILY/WEEKLY spielt der Ankertag keine Rolle. */
    public static LocalDate nextOccurrence(LocalDate date, RecurrenceType recurrenceType, int anchorDay) {
        switch (recurrenceType) {
            case DAILY:
                return date.plusDays(1);
            case WEEKLY:
                return date.plusWeeks(1);
            case MONTHLY:
                return atAnchorDay(YearMonth.from(date).plusMonths(1), anchorDay);
            case YEARLY:
                return atAnchorDay(YearMonth.from(date).plusYears(1), anchorDay);
            default:
                throw new IllegalArgumentException("Unsupported recurrence type: " + recurrenceType);
        }
    }

    /** Erster Termin nach heute. */
    public static LocalDate firstOccurrenceAfter(LocalDate lastExecutionDate, RecurrenceType recurrenceType, int anchorDay, LocalDate today) {
        LocalDate next = nextOccurrence(lastExecutionDate, recurrenceType, anchorDay);
        while (!next.isAfter(today)) {
            next = nextOccurrence(next, recurrenceType, anchorDay);
        }
        return next;
    }

    /**
     * Ankertag für ein neu angelegtes Template (Entscheidung E3): Der bisherige Anker bleibt nur, wenn das Kaufdatum
     * unverändert ist und zum Anker passt (z. B. 28.02. bei Anker 31). Ein geändertes Datum gibt den neuen Tag vor.
     */
    public static int resolveAnchorDay(LocalDate newDate, LocalDate previousDate, Integer previousAnchorDay) {
        if (previousAnchorDay != null
                && newDate.equals(previousDate)
                && newDate.getDayOfMonth() == Math.min(previousAnchorDay, newDate.lengthOfMonth())) {
            return previousAnchorDay;
        }
        return newDate.getDayOfMonth();
    }

    private static LocalDate atAnchorDay(YearMonth yearMonth, int anchorDay) {
        return yearMonth.atDay(Math.min(anchorDay, yearMonth.lengthOfMonth()));
    }
}
