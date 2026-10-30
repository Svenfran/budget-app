package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

/**
 * Statistikseite (DIV-63, Codex F-001): eine Antwort basiert auf einem einzigen Datenbank-Snapshot, damit Ausgaben,
 * verfügbare Jahre und Saldo auch bei parallel committeten Änderungen zusammenpassen. Geprüft wird der deklarierte
 * Vertrag, nicht die Snapshot-Semantik von Postgres.
 */
class StatisticsServiceSpec extends Specification {

    def "getStatistics läuft in einer lesenden Transaktion mit REPEATABLE_READ"() {
        when:
        def transactional = StatisticsService.getMethod('getStatistics', Long, StatisticsPeriod, Integer, Integer)
                .getAnnotation(Transactional)

        then:
        transactional != null
        transactional.readOnly()
        transactional.isolation() == Isolation.REPEATABLE_READ
    }
}
