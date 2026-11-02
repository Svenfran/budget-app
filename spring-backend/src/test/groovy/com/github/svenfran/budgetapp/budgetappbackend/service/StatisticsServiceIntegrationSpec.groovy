package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod.*
import static java.time.LocalDate.of

/**
 * Statistikseite (DIV-63): Gruppenauswahl, Berechtigung und aktueller Saldo über StatisticsService. Die Berechnung
 * der Zeitreihe und Kategorien prüft StatisticsCalculatorSpec; hier geht es um das Zusammenspiel mit der Datenbank,
 * GroupService (Gruppen des Nutzers) und SpendingsOverviewService (Saldo, Konsistenz mit der Übersicht, AC13).
 *
 * - Alle Mitgliedschaften beginnen am 01.01.2024, Einkäufe liegen 2025.
 * - Bedingungen enthalten nur IDs und DTO-Werte, keine Entities.
 */
class StatisticsServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsServiceIntegrationSpec)
    private static final LocalDate SEIT = of(2024, 1, 1)

    @Autowired StatisticsService statisticsService
    @Autowired SpendingsOverviewService spendingsOverviewService
    @Autowired GroupService groupService
    @Autowired CartService cartService
    @Autowired UserRepository userRepository

    def "AC3: Einzelne Gruppe zeigt nur deren Ausgaben und Saldo"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, members: [fixtures.user(name: 'Ben')], memberSince: SEIT)
        def urlaub = fixtures.group(owner: anna, members: [fixtures.user(name: 'Cleo')], memberSince: SEIT)
        einkauf(haushalt, anna, 60d, of(2025, 3, 1))
        einkauf(haushalt, haushalt.members[0], 40d, of(2025, 4, 1))
        einkauf(urlaub, anna, 30d, of(2024, 8, 1))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (Anna 60, Ben 40 in 2025), Urlaub {} (Anna 30 in 2024)',
                anna.id, haushalt.group.id, urlaub.group.id)

        when:
        def dto = statistik(haushalt.group.id, YEAR, 2025)

        then:
        dto.groupId == haushalt.group.id
        dto.totalSpent == 100d
        dto.paidByUser == 60d
        dto.paidCartCount == 1L
        dto.currentBalance == 10d   // Anteil 50, bezahlt 60
        dto.availableYears == [2025]
    }

    def "AC4/AC15: Alle Gruppen aggregiert die Gruppen des Nutzers; eine Gruppe, aus der er entfernt wurde, zählt nicht"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, members: [fixtures.user(name: 'Ben')], memberSince: SEIT)
        def urlaub = fixtures.group(members: [anna], memberSince: SEIT)
        def frueher = fixtures.group(memberSince: SEIT)
        fixtures.membership(frueher, anna, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2025, 1, 31)))
        einkauf(haushalt, anna, 60d, of(2025, 3, 1))
        einkauf(haushalt, haushalt.members[0], 40d, of(2025, 4, 1))
        einkauf(urlaub, anna, 30d, of(2024, 8, 1))
        einkauf(urlaub, urlaub.owner, 10d, of(2025, 8, 1))
        einkauf(frueher, anna, 999d, of(2025, 1, 10))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (Owner, Anna 60, Ben 40), Urlaub {} (Mitglied, Anna 30 in 2024, Owner 10), ' +
                'früher {} (entfernt, Anna 999)', anna.id, haushalt.group.id, urlaub.group.id, frueher.group.id)

        when:
        def jahr = statistik(null, YEAR, 2025)
        def alle = statistik(null, ALL, null)

        then:
        jahr.groupId == null
        jahr.totalSpent == 110d
        jahr.paidByUser == 60d
        jahr.paidCartCount == 1L
        // Haushalt: bezahlt 60, Anteil 50 → +10; Urlaub: bezahlt 30, Anteil 20 → +10
        jahr.currentBalance == 20d
        jahr.availableYears == [2025, 2024]

        and: "ALL: jeder Cart genau einmal"
        alle.totalSpent == 140d
        alle.paidByUser == 90d
        alle.paidCartCount == 2L
        alle.currentBalance == 20d
        alle.timeline*.year == [2024, 2025]
        alle.timeline*.amount == [30d, 60d]
    }

    def "AC4: Steht der Owner zugleich in members, zählen Carts und Saldo seiner Gruppe trotzdem nur einmal"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [anna, ben], memberSince: SEIT)
        einkauf(wg, anna, 50d, of(2025, 2, 1))
        flushAndClear()
        loginAs(anna)
        def gid = wg.group.id
        def gruppenIds = groupService.getGroupsByMemberOrOwner().map { it.id }.toList()
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner und Mitglied), Ben {}; getGroupsByMemberOrOwner -> {}',
                gid, anna.id, ben.id, gruppenIds)

        when:
        def dto = statistik(null, ALL, null)
        def uebersicht = saldoUebersicht(anna, gid)

        then: "Vorbedingung: die Gruppe kommt doppelt"
        gruppenIds == [gid, gid]

        and:
        dto.totalSpent == 50d
        dto.paidByUser == 50d
        dto.paidCartCount == 1L
        dto.currentBalance == uebersicht
        dto.currentBalance == 25d
    }

    def "AC10/E3: Saldo +50 und −20 ergibt +30 in allen Zeiträumen; eine einzelne Gruppe zeigt nur ihren Saldo"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def a = fixtures.group(owner: anna, members: [fixtures.user(name: 'Ben')], memberSince: SEIT)
        def b = fixtures.group(owner: anna, members: [fixtures.user(name: 'Cleo')], memberSince: SEIT)
        einkauf(a, anna, 100d, of(2025, 3, 1))
        einkauf(b, b.members[0], 40d, of(2025, 5, 1))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Gruppe A {} (Anna zahlt 100 → +50), Gruppe B {} (Cleo zahlt 40 → −20)',
                anna.id, a.group.id, b.group.id)

        when:
        def salden = [statistik(null, YEAR, 2024), statistik(null, YEAR, 2025), statistik(null, MONTH, 2025, 3),
                      statistik(null, ALL, null)]*.currentBalance
        def nurA = statistik(a.group.id, YEAR, 2024).currentBalance
        def nurB = statistik(b.group.id, ALL, null).currentBalance

        then:
        salden == [30d, 30d, 30d, 30d]
        nurA == 50d
        nurB == -20d
    }

    def "AC13: Werte von #nutzer stimmen centgenau mit der Übersicht überein (Jahr, Monat, Saldo; mit Ausgleichszahlung und Beitritt)"() {
        given: "Anna (Owner) und Ben seit 2024, Cleo ab 01.03.2025; ungerade Beträge, Ausgleichszahlung Ben → Anna"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        // Cleo muss in members stehen, um selbst abfragen zu dürfen; erst verknüpfen, dann die Historie (siehe Fixtures.group)
        wg.group.addMember(cleo)
        userRepository.save(cleo)
        fixtures.membership(wg, cleo, membershipStart: DateUtils.toDate(of(2025, 3, 1)))
        def gid = wg.group.id
        einkauf(wg, anna, 90d, of(2025, 1, 10))
        einkauf(wg, ben, 60.3d, of(2025, 3, 10))
        einkauf(wg, cleo, 33.33d, of(2025, 3, 15))
        einkauf(wg, anna, 12.01d, of(2024, 11, 2))
        zahlung(ben, anna, 20d, wg, of(2025, 4, 1))
        def betrachter = [Anna: anna, Ben: ben, Cleo: cleo][nutzer]
        def betrachterId = betrachter.id
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {} (ab 2025-03-01), Betrachter {}',
                gid, anna.id, ben.id, cleo.id, betrachterId)

        when:
        loginAs(userRepository.findById(betrachterId).get())
        def jahr = statistik(gid, YEAR, 2025)
        def maerz = statistik(gid, MONTH, 2025, 3)
        def jahrUebersicht = spendingsOverviewService.getSpendingsForGroupAndYear(2025, gid)
        def maerzUebersicht = jahrUebersicht.spendingsPerMonth.find { it.month == 3 }
        def saldo = saldoUebersicht(betrachter, gid)
        LOG.info('Übersicht 2025: sumTotalYear {}, Nutzer {}; März: sumTotalMonth {}, Nutzer {}; Saldo {}',
                jahrUebersicht.spendingsTotalYear.sumTotalYear,
                jahrUebersicht.spendingsTotalYear.spendingsTotalUser.find { it.userId == betrachterId }?.sum,
                maerzUebersicht.sumTotalMonth, maerzUebersicht.spendingsMonthlyUser.find { it.userId == betrachterId }?.sum,
                saldo)

        then:
        jahr.totalSpent == jahrUebersicht.spendingsTotalYear.sumTotalYear
        jahr.paidByUser == jahrUebersicht.spendingsTotalYear.spendingsTotalUser.find { it.userId == betrachterId }.sum
        maerz.totalSpent == maerzUebersicht.sumTotalMonth
        maerz.paidByUser == maerzUebersicht.spendingsMonthlyUser.find { it.userId == betrachterId }.sum
        jahr.currentBalance == saldo
        maerz.currentBalance == saldo

        and: "die Ausgleichszahlung erscheint als eigene Kategoriezeile, zählt aber nicht als bezahlter Einkauf"
        jahr.categories.findAll { it.settlement }*.amount == ausgleich
        jahr.paidCartCount == einkaeufeJahr
        maerz.paidCartCount == einkaeufeMaerz

        where:
        nutzer || ausgleich | einkaeufeJahr | einkaeufeMaerz
        'Anna' || [-20d]    | 1L            | 0L
        'Ben'  || [20d]     | 1L            | 1L
        'Cleo' || []        | 1L            | 1L
    }

    def "AC15: #fall erhält für die Gruppe NotOwnerOrMemberOfGroupException"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def wg = fixtures.group(memberSince: SEIT)
        if (entfernt) {
            fixtures.membership(wg, anna, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2025, 1, 31)))
        }
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Owner {}, Anna {} ({})', wg.group.id, wg.owner.id, anna.id, fall)

        when:
        LOG.info('statisticsService.getStatistics(Gruppe {}, YEAR 2025), erwartet NotOwnerOrMemberOfGroupException', wg.group.id)
        statisticsService.getStatistics(wg.group.id, YEAR, 2025, null)

        then:
        thrown(NotOwnerOrMemberOfGroupException)

        where:
        fall                  | entfernt
        'Fremde'              | false
        'Entferntes Mitglied' | true
    }

    def "F-002: Ohne eigene Carts: Gesamtausgaben sichtbar, bezahlt 0, Zeitreihe nur 0, keine Kategorien, Saldo wie Übersicht"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        einkauf(wg, ben, 80d, of(2025, 4, 1))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner, keine Carts), Ben {} zahlt 80 in 2025', wg.group.id, anna.id, ben.id)

        when:
        def dto = statistik(wg.group.id, YEAR, 2025)
        def saldo = saldoUebersicht(anna, wg.group.id)

        then:
        dto.totalSpent == 80d
        dto.paidByUser == 0d
        dto.paidCartCount == 0L
        dto.timeline.size() == 12
        dto.timeline*.amount.unique() == [0d]
        dto.categories == []
        dto.currentBalance == saldo
        dto.currentBalance == -40d
    }

    def "AC12: Im Zeitraum keine Carts: alle Ausgaben 0, 12 leere Monate, keine Kategorien, Saldo unverändert"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        einkauf(wg, anna, 30d, of(2025, 4, 1))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} zahlt 30 in 2025, Ben {}; Abfrage 2024', wg.group.id, anna.id, ben.id)

        when:
        def leer = statistik(wg.group.id, YEAR, 2024)
        def alle = statistik(wg.group.id, ALL, null)

        then:
        leer.totalSpent == 0d
        leer.paidByUser == 0d
        leer.paidCartCount == 0L
        leer.timeline*.month == (1..12).toList()
        leer.timeline*.amount.unique() == [0d]
        leer.categories == []
        leer.currentBalance == 15d
        leer.currentBalance == alle.currentBalance
        leer.availableYears == [2025]
    }

    private StatisticsDto statistik(Long groupId, period, Integer year, Integer month = null) {
        def dto = statisticsService.getStatistics(groupId, period, year, month)
        LOG.info('statisticsService.getStatistics(Gruppe {}, {} {}/{}) -> totalSpent {}, paidByUser {}, paidCartCount {}, ' +
                'currentBalance {}, timeline {}, categories {}', groupId ?: 'alle', period, year, month, dto.totalSpent,
                dto.paidByUser, dto.paidCartCount, dto.currentBalance, dto.timeline*.amount, dto.categories.collect { "${it.name}=${it.amount}" })
        dto
    }

    /** diff des Nutzers in der Gesamtübersicht (aktueller offener Saldo). */
    private Double saldoUebersicht(User nutzer, Long groupId) {
        spendingsOverviewService.getSpendingsForGroupAndAllYears(groupId)
                .spendingsTotalYear.spendingsTotalUser.find { it.userId == nutzer.id }?.diff
    }

    private void einkauf(GroupScenario wg, User zahler, Double betrag, LocalDate datum) {
        fixtures.cart(wg, user: zahler, amount: betrag, datePurchased: DateUtils.toDate(datum))
    }

    /** Ausgleichszahlung über CartService, wie sie die App anlegt (Sender- und Empfänger-Cart). */
    private void zahlung(User von, User an, Double betrag, GroupScenario wg, LocalDate tag) {
        loginAs(userRepository.findById(von.id).get())
        cartService.addSettlementPayment(new SettlementPaymentDto(betrag, wg.group.id, new UserDto(an), DateUtils.toDate(tag)))
        flushAndClear()
        LOG.info('cartService.addSettlementPayment(User {} an User {}, {})', von.id, an.id, betrag)
    }
}
