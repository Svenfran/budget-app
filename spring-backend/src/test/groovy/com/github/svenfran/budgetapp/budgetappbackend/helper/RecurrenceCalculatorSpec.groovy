package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType.*

/**
 * Folgetermine vom Ankertag aus (DIV-25): nur in Monaten ohne den Ankertag wird auf das Monatsende gekappt, danach
 * geht es wieder auf dem Ankertag weiter (keine Drift).
 */
class RecurrenceCalculatorSpec extends Specification {

    def "#recurrenceType ab #datum mit Anker #anker ergibt #erwartet"() {
        expect:
        RecurrenceCalculator.nextOccurrence(datum, recurrenceType, anker) == erwartet

        where:
        recurrenceType | datum                     | anker || erwartet
        MONTHLY        | LocalDate.of(2025, 1, 31) | 31    || LocalDate.of(2025, 2, 28)
        MONTHLY        | LocalDate.of(2025, 2, 28) | 31    || LocalDate.of(2025, 3, 31)
        MONTHLY        | LocalDate.of(2025, 2, 28) | 30    || LocalDate.of(2025, 3, 30)
        MONTHLY        | LocalDate.of(2025, 3, 31) | 31    || LocalDate.of(2025, 4, 30)
        MONTHLY        | LocalDate.of(2024, 1, 31) | 31    || LocalDate.of(2024, 2, 29)
        MONTHLY        | LocalDate.of(2024, 2, 29) | 30    || LocalDate.of(2024, 3, 30)
        MONTHLY        | LocalDate.of(2025, 6, 15) | 15    || LocalDate.of(2025, 7, 15)
        YEARLY         | LocalDate.of(2024, 2, 29) | 29    || LocalDate.of(2025, 2, 28)
        YEARLY         | LocalDate.of(2027, 2, 28) | 29    || LocalDate.of(2028, 2, 29)
        YEARLY         | LocalDate.of(2025, 6, 15) | 15    || LocalDate.of(2026, 6, 15)
        // Tages- und Wochenschritte ignorieren den Anker
        DAILY          | LocalDate.of(2025, 1, 31) | 15    || LocalDate.of(2025, 2, 1)
        WEEKLY         | LocalDate.of(2025, 1, 31) | 15    || LocalDate.of(2025, 2, 7)
    }

    def "Monatlich ab dem 31.01. bleibt über mehrere Schritte auf dem Monatsende und driftet nicht"() {
        expect:
        RecurrenceCalculator.firstOccurrenceAfter(
                LocalDate.of(2025, 1, 31),
                MONTHLY, 31,
                LocalDate.of(2025, 4, 1)) == LocalDate.of(2025, 4, 30)
    }

    def "Liegt der letzte Termin auf heute, ist der erste Termin nach heute der nächste Schritt"() {
        expect:
        RecurrenceCalculator.firstOccurrenceAfter(
                LocalDate.of(2025, 2, 28),
                MONTHLY, 31,
                LocalDate.of(2025, 2, 28)) == LocalDate.of(2025, 3, 31)
    }

    def "Neues Datum #neu, altes Datum #alt, alter Anker #alterAnker: Anker #erwartet (#fall)"() {
        expect:
        RecurrenceCalculator.resolveAnchorDay(neu, alt, alterAnker) == erwartet

        where:
        fall                                          | neu                       | alt                       | alterAnker || erwartet
        'Datum unverändert, gekappt im Februar'       | LocalDate.of(2025, 2, 28) | LocalDate.of(2025, 2, 28) | 31         || 31
        'Datum unverändert, gekappt im April'         | LocalDate.of(2025, 4, 30) | LocalDate.of(2025, 4, 30) | 31         || 31
        'Datum geändert auf die gekappte Form'        | LocalDate.of(2025, 4, 30) | LocalDate.of(2025, 3, 31) | 31         || 30
        'Datum geändert auf den 28.02.'               | LocalDate.of(2025, 2, 28) | LocalDate.of(2025, 1, 31) | 31         || 28
        'Datum geändert auf den Ankertag'             | LocalDate.of(2025, 3, 31) | LocalDate.of(2025, 2, 28) | 31         || 31
        'Datum geändert auf einen anderen Tag'        | LocalDate.of(2025, 3, 15) | LocalDate.of(2025, 3, 31) | 31         || 15
        'Datum früher ohne Template-Update verschoben' | LocalDate.of(2025, 3, 15) | LocalDate.of(2025, 3, 15) | 31         || 15
        'neuer Einkauf ohne Template'                 | LocalDate.of(2025, 1, 31) | null                      | null       || 31
    }

    def "Ohne Wiederholung gibt es keinen Folgetermin"() {
        when:
        RecurrenceCalculator.nextOccurrence(LocalDate.of(2025, 1, 31), NONE, 31)

        then:
        thrown(IllegalArgumentException)
    }
}
