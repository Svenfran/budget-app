package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod.*
import static java.time.LocalDate.of

/**
 * Statistikseite (DIV-63): Gruppenauswahl, Berechtigung und aktueller Saldo über StatisticsService. Die Berechnung
 * der Zeitreihe und Kategorien prüft StatisticsCalculatorSpec; hier geht es um das Zusammenspiel mit der Datenbank,
 * GroupService (Gruppen des Nutzers) und SpendingsOverviewService (Saldo, Konsistenz mit der Übersicht, AC13).
 * DIV-72: Gruppenblock (groupView) mit Mitgliederzahl, Gruppenauswahl und centgenauem Abgleich mit der Übersicht (AC9).
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
    @Autowired SettlementService settlementService
    @Autowired UserRepository userRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired CategoryService categoryService

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
        statisticsService.getStatistics(wg.group.id, null, YEAR, 2025, null)

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

    // --- Gruppenperspektive (DIV-72) ---

    def "DIV-72 AC3: Einzelne Gruppe: Gruppenblock 175,00 €, 3 Ausgaben, Ø 58,33 €, 3 Mitglieder; persönlich 125,00 €"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben, fixtures.user(name: 'Cleo')], memberSince: SEIT)
        einkauf(wg, anna, 100d, of(2025, 3, 1))
        einkauf(wg, ben, 50d, of(2025, 3, 2))
        einkauf(wg, anna, 25d, of(2025, 5, 1))
        def kategorie = wg.category.name
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner) 100 + 25, Ben {} 50, Cleo (Mitglied, ohne Carts); Kategorie {}',
                wg.group.id, anna.id, ben.id, kategorie)

        when:
        def dto = statistik(wg.group.id, YEAR, 2025)

        then:
        dto.totalSpent == 175d
        dto.groupView.cartCount == 3L
        dto.groupView.averagePerCart == 58.33d
        dto.groupView.memberCount == 3
        dto.groupView.categories*.name == [kategorie]
        dto.groupView.categories*.amount == [175d]
        dto.groupView.timeline*.amount.take(5) == [0d, 0d, 150d, 0d, 25d]

        and: "AC4: persönlich unverändert"
        dto.paidByUser == 125d
        dto.timeline*.amount.take(5) == [0d, 0d, 100d, 0d, 25d]
        dto.categories*.amount == [125d]
    }

    def "DIV-72: Steht der Owner zugleich in members, zählt er bei den Mitgliedern einmal (#auswahl)"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [anna, ben], memberSince: SEIT)
        def gid = wg.group.id
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner und Mitglied), Ben {}; Auswahl {}', gid, anna.id, ben.id, auswahl)

        when:
        def dto = statistik(auswahl == 'Gruppe' ? gid : null, ALL, null)

        then:
        dto.groupView.memberCount == 2

        where:
        auswahl << ['Gruppe', 'alle Gruppen']
    }

    def "DIV-72 E1/AC7: Alle Gruppen: Gruppenblock über die Gruppen des Nutzers, jeder Cart und jede Person einmal; fremde und verlassene Gruppen zählen nicht"() {
        given: "Haushalt (Anna Owner, Ben), Urlaub (Olaf Owner, Anna, Ben, Cleo), fremde Gruppe, Gruppe, die Anna verlassen hat"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def olaf = fixtures.user(name: 'Olaf')
        def haushalt = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        def urlaub = fixtures.group(owner: olaf, members: [anna, ben, cleo], memberSince: SEIT)
        def fremd = fixtures.group(memberSince: SEIT)
        def frueher = fixtures.group(memberSince: SEIT)
        fixtures.membership(frueher, anna, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2025, 1, 31)))
        einkauf(haushalt, anna, 60d, of(2025, 3, 1))
        einkauf(haushalt, ben, 40d, of(2025, 4, 1))
        einkauf(urlaub, olaf, 30d, of(2025, 8, 1))
        einkauf(urlaub, cleo, 20d, of(2025, 8, 2))
        einkauf(fremd, fremd.owner, 500d, of(2025, 5, 1))
        einkauf(frueher, anna, 999d, of(2025, 1, 10))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {}, Ben {}, Cleo {}, Olaf {}; Haushalt {} (60 + 40), Urlaub {} (30 + 20), fremd {} (500), ' +
                'früher {} (Anna entfernt, 999)', anna.id, ben.id, cleo.id, olaf.id, haushalt.group.id, urlaub.group.id,
                fremd.group.id, frueher.group.id)

        when:
        def dto = statistik(null, YEAR, 2025)

        then:
        dto.totalSpent == 150d
        dto.groupView.cartCount == 4L
        dto.groupView.averagePerCart == 37.5d
        dto.groupView.memberCount == 4      // Anna, Ben, Olaf, Cleo
        cents(dto.groupView.timeline*.amount) == 15000L
        cents(dto.groupView.categories*.amount) == 15000L
        dto.groupView.categories.size() == 2

        and: "persönlich unverändert"
        dto.paidByUser == 60d
    }

    def "DIV-72 AC9: Gruppengesamtausgaben #period #year/#month stimmen centgenau mit der Übersicht überein (mit Ausgleichszahlung und ehemaligem Mitglied)"() {
        given: "Anna (Owner) und Ben seit 2024, Dora nur 2024 Mitglied; Ausgleichszahlung Ben → Anna im März 2025"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def dora = fixtures.user(name: 'Dora')
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        fixtures.membership(wg, dora, membershipStart: DateUtils.toDate(SEIT), membershipEnd: DateUtils.toDate(of(2024, 12, 31)))
        def gid = wg.group.id
        einkauf(wg, anna, 90d, of(2025, 1, 10))
        einkauf(wg, ben, 60.3d, of(2025, 3, 10))
        einkauf(wg, dora, 33.33d, of(2024, 6, 1))
        einkauf(wg, anna, 12.01d, of(2024, 11, 2))
        zahlung(ben, anna, 20d, wg, of(2025, 3, 20))
        def annaId = anna.id
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Dora {} (Mitglied nur 2024)', gid, annaId, ben.id, dora.id)

        when:
        loginAs(userRepository.findById(annaId).get())
        def dto = statistik(gid, period, year, month)
        def uebersicht = period == ALL
                ? spendingsOverviewService.getSpendingsForGroupAndAllYears(gid).spendingsTotalYear.sumTotalYear
                : period == YEAR
                ? spendingsOverviewService.getSpendingsForGroupAndYear(year, gid).spendingsTotalYear.sumTotalYear
                : spendingsOverviewService.getSpendingsForGroupAndYear(year, gid).spendingsPerMonth.find { it.month == month }.sumTotalMonth
        LOG.info('Übersicht {} {}/{} -> {}', period, year, month, uebersicht)

        then:
        dto.totalSpent == uebersicht
        dto.totalSpent == gesamt
        cents(dto.groupView.timeline*.amount) == cents([gesamt])
        cents(dto.groupView.categories*.amount) == cents([gesamt])
        dto.groupView.cartCount == anzahl

        where: "die Ausgleichszahlung (+20/−20) ändert keine Summe und zählt nicht als Ausgabe"
        period | year | month || gesamt  | anzahl
        MONTH  | 2025 | 3     || 60.3d   | 1L
        YEAR   | 2025 | null  || 150.3d  | 2L
        YEAR   | 2024 | null  || 45.34d  | 2L
        ALL    | null | null  || 195.64d | 4L
    }

    private static long cents(List<Double> betraege) {
        betraege.sum(0L) { Math.round(it * 100) } as long
    }

    private StatisticsDto statistik(Long groupId, period, Integer year, Integer month = null) {
        statistikIn(null, groupId, period, year, month)
    }

    /** Wie statistik(), mit Währungsparameter (DIV-77; null = nicht gesendet). */
    private StatisticsDto statistikIn(String currency, Long groupId, period, Integer year, Integer month = null) {
        def dto = statisticsService.getStatistics(groupId, currency, period, year, month)
        LOG.info('statisticsService.getStatistics(Gruppe {}, currency {}, {} {}/{}) -> currencyCode {}, availableCurrencies {}, ' +
                'availableYears {}, totalSpent {}, paidByUser {}, paidCartCount {}, ' +
                'currentBalance {}, timeline {}, categories {}; groupView: cartCount {}, averagePerCart {}, memberCount {}, ' +
                'timeline {}, categories {}', groupId ?: 'alle', currency, period, year, month, dto.currencyCode,
                dto.availableCurrencies, dto.availableYears, dto.totalSpent,
                dto.paidByUser, dto.paidCartCount, dto.currentBalance, dto.timeline*.amount, dto.categories.collect { "${it.name}=${it.amount}" },
                dto.groupView.cartCount, dto.groupView.averagePerCart, dto.groupView.memberCount, dto.groupView.timeline*.amount,
                dto.groupView.categories.collect { "${it.name}=${it.amount}" })
        dto
    }

    // ----- DIV-77: Währungen werden nie addiert -----

    def "Nur EUR-Gruppen: Zahlen wie bisher, currencyCode EUR, availableCurrencies [EUR]"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, members: [fixtures.user(name: 'Ben')], memberSince: SEIT)
        einkauf(haushalt, anna, 60d, of(2025, 3, 1))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (EUR, Anna 60 in 2025)', anna.id, haushalt.group.id)

        when:
        def ohne = statistikIn(null, null, YEAR, 2025)
        def mitEur = statistikIn('EUR', null, YEAR, 2025)

        then:
        [ohne, mitEur].every { it.currencyCode == 'EUR' && it.availableCurrencies == ['EUR'] && it.totalSpent == 60d }
        ohne.currentBalance == 30d
    }

    def "Alle Gruppen mit EUR und USD: currency=USD zählt nur USD-Gruppen, gleichnamige Kategorien werden nicht zusammengeführt"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def haushalt = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        def urlaub = fixtures.group(owner: anna, members: [cleo, ben], memberSince: SEIT)
        def eurKategorie = fixtures.category(haushalt, name: 'Essen')
        def usdKategorie = fixtures.category(urlaub, name: 'Essen')
        fixtures.cart(haushalt, user: anna, amount: 100d, category: eurKategorie, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        fixtures.cart(urlaub, user: anna, amount: 90d, category: usdKategorie, datePurchased: DateUtils.toDate(of(2025, 8, 1)))
        fixtures.cart(urlaub, user: cleo, amount: 30d, category: usdKategorie, datePurchased: DateUtils.toDate(of(2025, 8, 2)))
        flushAndClear()
        waehrung(urlaub, SupportedCurrency.USD)
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (EUR, Ben {}; Anna 100 Essen), Urlaub {} (USD, Cleo {}, Ben; Anna 90 und Cleo 30 Essen)',
                anna.id, haushalt.group.id, ben.id, urlaub.group.id, cleo.id)

        when:
        def usd = statistikIn('USD', null, YEAR, 2025)
        def eur = statistikIn('EUR', null, YEAR, 2025)

        then: "USD: nur die Urlaubsgruppe"
        usd.currencyCode == 'USD'
        usd.availableCurrencies == ['EUR', 'USD']
        usd.totalSpent == 120d
        usd.paidByUser == 90d
        usd.paidCartCount == 1L
        usd.currentBalance == 50d   // bezahlt 90, Anteil 40
        usd.categories.collect { [it.name, it.amount] } == [['Essen', 90d]]
        usd.groupView.cartCount == 2L
        usd.groupView.averagePerCart == 60d
        usd.groupView.memberCount == 3
        usd.groupView.categories.collect { [it.name, it.amount] } == [['Essen', 120d]]

        and: "EUR: nur der Haushalt"
        eur.currencyCode == 'EUR'
        eur.availableCurrencies == ['EUR', 'USD']
        eur.totalSpent == 100d
        eur.currentBalance == 50d
        eur.groupView.memberCount == 2
        eur.groupView.categories.collect { [it.name, it.amount] } == [['Essen', 100d]]
    }

    def "Ohne currency gilt EUR, auch wenn der Nutzer mehr USD- als EUR-Gruppen hat"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, memberSince: SEIT)
        def reise1 = fixtures.group(owner: anna, memberSince: SEIT)
        def reise2 = fixtures.group(owner: anna, memberSince: SEIT)
        einkauf(haushalt, anna, 10d, of(2025, 3, 1))
        einkauf(reise1, anna, 200d, of(2025, 3, 1))
        einkauf(reise2, anna, 300d, of(2025, 3, 1))
        flushAndClear()
        waehrung(reise1, SupportedCurrency.USD)
        waehrung(reise2, SupportedCurrency.USD)
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (EUR, 10), Reise {} und {} (USD, 200 und 300); Abfrage ohne currency',
                anna.id, haushalt.group.id, reise1.group.id, reise2.group.id)

        when:
        def dto = statistikIn(null, null, ALL, null)

        then:
        dto.currencyCode == 'EUR'
        dto.totalSpent == 10d
        dto.availableCurrencies == ['EUR', 'USD']
    }

    def "Nur USD-Gruppen, Abfrage #fall: leere Zahlen ohne Fehler, availableCurrencies [USD]"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def reise = fixtures.group(owner: anna, members: [fixtures.user(name: 'Ben')], memberSince: SEIT)
        einkauf(reise, anna, 200d, of(2025, 3, 1))
        flushAndClear()
        waehrung(reise, SupportedCurrency.USD)
        loginAs(anna)
        LOG.info('Szenario: Anna {}; nur Reise {} (USD, 200 in 2025); currency {}', anna.id, reise.group.id, currency)

        when:
        def dto = statistikIn(currency, null, YEAR, 2025)

        then:
        dto.currencyCode == erwartet
        dto.availableCurrencies == ['USD']
        dto.totalSpent == 0d
        dto.paidByUser == 0d
        dto.paidCartCount == 0L
        dto.currentBalance == 0d
        dto.categories.isEmpty()
        dto.availableYears.isEmpty()
        dto.groupView.cartCount == 0L
        dto.groupView.memberCount == 0

        where:
        fall                         | currency || erwartet
        'ohne currency (EUR)'        | null     || 'EUR'
        'GBP ohne passende Gruppe'   | 'GBP'    || 'GBP'
    }

    def "availableYears enthält keine Jahre, die nur in Gruppen anderer Währung vorkommen"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, memberSince: SEIT)
        def reise = fixtures.group(owner: anna, memberSince: SEIT)
        einkauf(haushalt, anna, 10d, of(2025, 3, 1))
        einkauf(reise, anna, 20d, of(2024, 6, 1))
        flushAndClear()
        waehrung(reise, SupportedCurrency.USD)
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (EUR, 2025), Reise {} (USD, 2024)', anna.id, haushalt.group.id, reise.group.id)

        when:
        def eur = statistikIn('EUR', null, ALL, null)
        def usd = statistikIn('USD', null, ALL, null)

        then:
        eur.availableYears == [2025]
        usd.availableYears == [2024]
    }

    def "Mit groupId gilt die Gruppenwährung, ein gesendetes currency wird ignoriert"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def haushalt = fixtures.group(owner: anna, memberSince: SEIT)
        def reise = fixtures.group(owner: anna, memberSince: SEIT)
        einkauf(haushalt, anna, 10d, of(2025, 3, 1))
        einkauf(reise, anna, 20d, of(2025, 3, 1))
        flushAndClear()
        waehrung(reise, SupportedCurrency.CHF)
        loginAs(anna)
        LOG.info('Szenario: Anna {}; Haushalt {} (EUR, 10), Reise {} (CHF, 20); Abfrage Reise mit currency=EUR',
                anna.id, haushalt.group.id, reise.group.id)

        when:
        def dto = statistikIn('EUR', reise.group.id, ALL, null)

        then:
        dto.groupId == reise.group.id
        dto.currencyCode == 'CHF'
        dto.totalSpent == 20d
        dto.groupView.cartCount == 1L
        dto.availableCurrencies == ['EUR', 'CHF']
    }

    /** Gruppenwährung direkt setzen (Spalte ist updatable = false, Muster GroupService.updateGroup). */
    private void waehrung(GroupScenario wg, SupportedCurrency currency) {
        entityManager.createQuery('update Group g set g.currencyCode = :c where g.id = :id')
                .setParameter('c', currency).setParameter('id', wg.group.id).executeUpdate()
    }

    /** diff des Nutzers in der Gesamtübersicht (aktueller offener Saldo). */
    private Double saldoUebersicht(User nutzer, Long groupId) {
        spendingsOverviewService.getSpendingsForGroupAndAllYears(groupId)
                .spendingsTotalYear.spendingsTotalUser.find { it.userId == nutzer.id }?.diff
    }

    def "DIV-74 AC10: Ausgleichszahlungen werden nach Umbenennen der Kategorie und bei Anlage in anderer Sprache erkannt"() {
        given: "Anna (Owner) zahlt 90 €; Ben → Anna 20 € (de); Kategorie umbenannt in 'Rückzahlungen'; Anna → Ben 5 € (en)"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: SEIT)
        def gid = wg.group.id
        einkauf(wg, anna, 90d, of(2025, 1, 10))
        zahlung(ben, anna, 20d, wg, of(2025, 4, 1))
        def kategorie = categoryRepository.findAllByGroup_IdOrderByName(gid).find { it.settlement }
        loginAs(userRepository.findById(anna.id).get())
        categoryService.updateCategory(new CategoryDto(id: kategorie.id, name: 'Rückzahlungen', groupId: gid))
        flushAndClear()
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        zahlung(anna, ben, 5d, wg, of(2025, 5, 1))
        def kategorienDerGruppe = categoryRepository.findAllByGroup_IdOrderByName(gid).collect { [it.name, it.settlement] }
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}; Settlement-Kategorie {} umbenannt; Kategorien {}', gid, anna.id,
                ben.id, kategorie.id, kategorienDerGruppe)

        when:
        loginAs(userRepository.findById(ben.id).get())
        def jahr = statistik(gid, YEAR, 2025)

        then: "eine Settlement-Zeile über beide Zahlungen, unabhängig von Name und Sprache; kein bezahlter Einkauf für Ben"
        jahr.categories.findAll { it.settlement }*.amount == [15d]
        jahr.categories.findAll { !it.settlement }.isEmpty()
        jahr.paidCartCount == 0L
        jahr.groupView.cartCount == 1L

        and: "keine zweite Settlement-Kategorie in Request-Sprache"
        !kategorienDerGruppe.any { it[0] == 'Repayment' }
        kategorienDerGruppe.findAll { it[1] }*.getAt(0) == ['Rückzahlungen']

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    private void einkauf(GroupScenario wg, User zahler, Double betrag, LocalDate datum) {
        fixtures.cart(wg, user: zahler, amount: betrag, datePurchased: DateUtils.toDate(datum))
    }

    /** Ausgleichszahlung über SettlementService, wie sie die App anlegt (Sender- und Empfänger-Cart). */
    private void zahlung(User von, User an, Double betrag, GroupScenario wg, LocalDate tag) {
        loginAs(userRepository.findById(von.id).get())
        settlementService.addSettlementPayment(new SettlementPaymentDto(betrag, wg.group.id, new UserDto(an), tag))
        flushAndClear()
        LOG.info('settlementService.addSettlementPayment(User {} an User {}, {})', von.id, an.id, betrag)
    }
}
