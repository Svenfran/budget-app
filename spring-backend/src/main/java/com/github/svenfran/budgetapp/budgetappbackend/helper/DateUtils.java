package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Small date conversion helpers shared across services. Centralizes the
 * recurring {@code Date <-> LocalDate} conversions that were previously
 * duplicated inline using {@code toInstant().atZone(...)} chains.
 */
public final class DateUtils {

    /** Sentinel used when a membership has no end date (open-ended). */
    public static final LocalDate NO_END_DATE = LocalDate.of(2999, 12, 31);

    private DateUtils() {
    }

    public static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
