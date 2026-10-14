package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Verstellbare Uhr für Integrations- und API-Specs; ersetzt die Produktions-Clock aus ApplicationConfig.
 *
 * - Standard: TestData.REFERENCE_DATE (Mitternacht in der Systemzeitzone, wie DateUtils).
 * - Verstellen per testClock.today = LocalDate.of(2025, 1, 31); IntegrationSpec setzt die Uhr nach jedem Feature zurück.
 * - Als @Component (wie Fixtures) statt @TestConfiguration: wird per Component-Scan gefunden, ohne die
 *   Kontext-Konfiguration zu ändern – also kein zusätzlicher Spring-Kontext.
 */
@Primary
@Component
class TestClock extends Clock {

    private final ZoneId zone = ZoneId.systemDefault()
    private Instant instant

    TestClock() {
        reset()
    }

    @Override
    ZoneId getZone() { zone }

    @Override
    Clock withZone(ZoneId zone) { Clock.fixed(instant, zone) }

    @Override
    Instant instant() { instant }

    LocalDate getToday() { LocalDate.ofInstant(instant, zone) }

    void setToday(LocalDate date) { instant = date.atStartOfDay(zone).toInstant() }

    void reset() { setToday(TestData.REFERENCE_DATE) }
}
