package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidStatisticsRequestException
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

/**
 * Statistikseite (DIV-63, Codex F-001): eine Antwort basiert auf einem einzigen Datenbank-Snapshot, damit Ausgaben,
 * verfügbare Jahre und Saldo auch bei parallel committeten Änderungen zusammenpassen. Geprüft wird der deklarierte
 * Vertrag, nicht die Snapshot-Semantik von Postgres.
 */
class StatisticsServiceSpec extends Specification {

    def dataLoaderService = Mock(DataLoaderService)
    def service = new StatisticsService(dataLoaderService, new VerificationService(Mock(PasswordEncoder), Mock(UserRepository)),
            Mock(GroupService), Mock(SpendingsOverviewService), Mock(CartRepository), Mock(Translator))

    def "getStatistics läuft in einer lesenden Transaktion mit REPEATABLE_READ"() {
        when:
        def transactional = StatisticsService.getMethod('getStatistics', Long, StatisticsPeriod, Integer, Integer)
                .getAnnotation(Transactional)

        then:
        transactional != null
        transactional.readOnly()
        transactional.isolation() == Isolation.REPEATABLE_READ
    }

    def "#period mit Jahr #year am Rand des LocalDate-Bereichs wird abgelehnt, bevor etwas geladen wird (Codex-Review F-001)"() {
        // Regression: year 999999999 bestand die Prüfung, die Kalenderwochen im Dezember reichten über LocalDate.MAX
        // hinaus und lösten eine DateTimeException (500) statt 400 aus.
        when:
        service.getStatistics(null, period, year, month)

        then:
        thrown(InvalidStatisticsRequestException)
        0 * dataLoaderService._

        where:
        period                  | year              | month
        StatisticsPeriod.MONTH  | 999_999_999       | 12
        StatisticsPeriod.MONTH  | -999_999_999      | 1
        StatisticsPeriod.YEAR   | 999_999_999       | null
        StatisticsPeriod.YEAR   | Integer.MAX_VALUE | null
    }
}
