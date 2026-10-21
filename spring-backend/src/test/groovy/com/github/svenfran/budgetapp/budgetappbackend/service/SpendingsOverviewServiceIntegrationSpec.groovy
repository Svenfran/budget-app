package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewUserDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.Duration
import java.time.LocalDate

import static java.time.LocalDate.of

/**
 * Jahres- und Monatswerte der Ausgabenübersicht mit Ein- und Austritten.
 *
 * - Anteile werden exakt aus Betrag und Mitgliedschaftshistorie am Kaufdatum berechnet, gerundet wird erst der Saldo
 *   je Periode, sodass die Salden zusammen 0 ergeben (DIV-38, D6). averagePerMember wird dafür nicht gelesen; wo gesetzt,
 *   dann wie von der App geschrieben.
 * - Nutzerwerte werden als [userId: [sum, diff]] verglichen; die Reihenfolge der Nutzer ist nicht garantiert.
 * - Anteil eines Nutzers = sum - diff.
 * - Summen von Salden werden in Cent verglichen (diffSummeInCent), nicht als Double-Addition.
 * - Bedingungen enthalten nur IDs, keine Entities: Spock rendert bei einem Fehlschlag jeden Teilausdruck, und
 *   IDs statt Entities halten die Fehlermeldung lesbar.
 */
class SpendingsOverviewServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SpendingsOverviewServiceIntegrationSpec)

    @Autowired SpendingsOverviewService spendingsOverviewService
    @Autowired GroupRepository groupRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository
    @Autowired CartRepository cartRepository
    @Autowired UserRepository userRepository
    @Autowired CartService cartService

    def "Austritt und Eintritt über den Jahreswechsel: Jahre 2024 und 2025 sowie Gesamtübersicht"() {
        given: "Anna (Owner) seit 01.01.2024, Ben 01.01.–31.12.2024, Cleo ab 01.01.2025 10 Uhr (Beitritt mit Uhrzeit)"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        def wg = fixtures.group(owner: anna, memberSince: of(2024, 1, 1))
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(of(2024, 1, 1)), membershipEnd: DateUtils.toDate(of(2024, 12, 31)))
        fixtures.membership(wg, cleo, membershipStart: at(of(2025, 1, 1), 10))

        and: "c1 31.12.2024 (Anna, Ben), c2 01.01.2025 (Anna, Cleo – zählt nur, weil die Uhrzeit des Beitritts abgeschnitten wird), c3 10.03.2025 (Anna, Cleo)"
        fixtures.cart(wg, user: anna, amount: 30d, averagePerMember: 15d, datePurchased: DateUtils.toDate(of(2024, 12, 31)))
        fixtures.cart(wg, user: cleo, amount: 20d, averagePerMember: 10d, datePurchased: DateUtils.toDate(of(2025, 1, 1)))
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 5d, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {} (2024), Cleo {} (ab 2025-01-01 10 Uhr); Einkäufe ' +
                '2024-12-31 Anna 30, 2025-01-01 Cleo 20, 2025-03-10 Anna 10', wg.group.id, annaId, benId, cleoId)

        when:
        def jahr2024 = spendingsOverviewService.getSpendingsForGroupAndYear(2024, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2024)', jahr2024)

        then:
        jahr2024.year == 2024
        jahr2024.availableYears == [2025, 2024]
        jahr2024.spendingsTotalYear.sumTotalYear == 30d   // nur c1
        // Anna: bezahlt c1 30, Anteil c1 15 → diff 15; Ben: Anteil c1 15 → diff −15; Cleo erst ab 2025 Mitglied
        werte(jahr2024.spendingsTotalYear.spendingsTotalUser) == [(annaId): [30d, 15d], (benId): [0d, -15d]]

        and: "Dezember: Ben ist im Austrittsmonat noch enthalten"
        jahr2024.spendingsPerMonth*.month == [12]
        jahr2024.spendingsPerMonth[0].sumTotalMonth == 30d
        werte(jahr2024.spendingsPerMonth[0].spendingsMonthlyUser) == [(annaId): [30d, 15d], (benId): [0d, -15d]]

        and: "I1/I2: keine doppelten Nutzer, Summe der Zahlungen = Jahressumme"
        jahr2024.spendingsTotalYear.spendingsTotalUser*.userId.sort() == [annaId, benId].sort()
        jahr2024.spendingsPerMonth[0].spendingsMonthlyUser*.userId.sort() == [annaId, benId].sort()
        jahr2024.spendingsTotalYear.spendingsTotalUser*.sum.sum() == jahr2024.spendingsTotalYear.sumTotalYear

        when:
        def jahr2025 = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr2025)

        then:
        jahr2025.spendingsTotalYear.sumTotalYear == 30d   // c2 20 + c3 10
        // Anna: bezahlt c3 10, Anteil c2 10 + c3 5 = 15 → diff −5; Cleo: bezahlt c2 20, Anteil 15 → diff 5; Ben ausgetreten 2024
        // (ohne Abschneiden der Beitrittsuhrzeit fehlte Cleos Anteil an c2: Anteil 5 → diff 15)
        werte(jahr2025.spendingsTotalYear.spendingsTotalUser) == [(annaId): [10d, -5d], (cleoId): [20d, 5d]]

        and: "Monate absteigend: März (c3), Januar (c2)"
        jahr2025.spendingsPerMonth*.month == [3, 1]
        jahr2025.spendingsPerMonth*.sumTotalMonth == [10d, 20d]
        werte(jahr2025.spendingsPerMonth[0].spendingsMonthlyUser) == [(annaId): [10d, 5d], (cleoId): [0d, -5d]]
        werte(jahr2025.spendingsPerMonth[1].spendingsMonthlyUser) == [(annaId): [0d, -10d], (cleoId): [20d, 10d]]

        and: "I1/I2 je Monat"
        jahr2025.spendingsPerMonth.collect { it.spendingsMonthlyUser*.userId.sort() } == [[annaId, cleoId].sort()] * 2
        jahr2025.spendingsPerMonth.collect { it.spendingsMonthlyUser*.sum.sum() } == jahr2025.spendingsPerMonth*.sumTotalMonth

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)

        then: "alle Nutzer der Historie; ausgetretene mit Zusatz REMOVED"
        gesamt.availableYears == [2025, 2024]
        gesamt.spendingsTotalYear.sumTotalYear == 60d   // 30 + 20 + 10 (ungerundetes Feld, exakte Beträge)
        // Anna: bezahlt 30 + 10 = 40, Anteil 15 + 10 + 5 = 30 → diff 10; Ben: Anteil 15 → −15; Cleo: bezahlt 20, Anteil 15 → 5
        werte(gesamt.spendingsTotalYear.spendingsTotalUser) == [(annaId): [40d, 10d], (benId): [0d, -15d], (cleoId): [20d, 5d]]
        gesamt.spendingsTotalYear.spendingsTotalUser.collectEntries { [it.userId, it.userName] } ==
                [(annaId): 'Anna', (benId): 'Ben REMOVED', (cleoId): 'Cleo']

        and: "I1–I3: jeder Nutzer einmal, Summe der Zahlungen = Gesamtsumme, Anteile ergeben zusammen die Gesamtsumme"
        gesamt.spendingsTotalYear.spendingsTotalUser*.userId.sort() == [annaId, benId, cleoId].sort()
        gesamt.spendingsTotalYear.spendingsTotalUser*.sum.sum() == 60d
        anteile(gesamt.spendingsTotalYear.spendingsTotalUser) == [(annaId): 30d, (benId): 15d, (cleoId): 15d]

        and: "je Jahr dieselben Werte wie in der Jahresansicht"
        gesamt.spendingsPerYear*.year == [2025, 2024]
        gesamt.spendingsPerYear*.sumTotalYear == [30d, 30d]
        werte(gesamt.spendingsPerYear[0].spendingsYearlyUser) == [(annaId): [10d, -5d], (cleoId): [20d, 5d]]
        werte(gesamt.spendingsPerYear[1].spendingsYearlyUser) == [(annaId): [30d, 15d], (benId): [0d, -15d]]

        and: "Fixture-Abgleich: Ben und Cleo stehen nur in der Historie, nicht in group.members"
        def mitgliederIds = groupRepository.findById(wg.group.id).get().members*.id
        mitgliederIds == []
    }

    def "Wiedereintritt am selben Tag und nach einer Lücke: jeder Cart zählt einmal, in der Lücke kein Anteil"() {
        given: "Anna (Owner) seit 01.01.2025"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def (annaId, benId) = [anna, ben]*.id
        def wg = fixtures.group(owner: anna, memberSince: of(2025, 1, 1))

        and: "Ben: entfernt am 28.02. um 18 Uhr, am selben Tag um 20 Uhr wieder hinzugefügt, am 31.03. entfernt, ab 01.05. wieder Mitglied"
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(of(2025, 1, 1)), membershipEnd: at(of(2025, 2, 28), 18))
        fixtures.membership(wg, ben, membershipStart: at(of(2025, 2, 28), 20), membershipEnd: at(of(2025, 3, 31), 18))
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(of(2025, 5, 1)))

        and: "der Cart vom 28.02. liegt in beiden ersten Mitgliedschaften, der vom 15.04. in der Lücke"
        fixtures.cart(wg, user: anna, amount: 20d, averagePerMember: 10d, datePurchased: DateUtils.toDate(of(2025, 2, 10)))
        fixtures.cart(wg, user: anna, amount: 8d, averagePerMember: 4d, datePurchased: DateUtils.toDate(of(2025, 2, 28)))
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 10d, datePurchased: DateUtils.toDate(of(2025, 4, 15)))
        fixtures.cart(wg, user: ben, amount: 30d, averagePerMember: 15d, datePurchased: DateUtils.toDate(of(2025, 5, 1)))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {} mit drei Mitgliedschaften; Einkäufe 2025-02-10, ' +
                '2025-02-28, 2025-04-15 Anna, 2025-05-01 Ben', wg.group.id, annaId, benId)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then:
        jahr.spendingsTotalYear.sumTotalYear == 68d   // 20 + 8 + 10 + 30
        // Anna: bezahlt 20 + 8 + 10 = 38, Anteil 10 + 4 + 10 + 15 = 39 → diff −1
        // Ben: bezahlt 30, Anteil 10 + 4 + 15 = 29 → diff 1
        werte(jahr.spendingsTotalYear.spendingsTotalUser) == [(annaId): [38d, -1d], (benId): [30d, 1d]]
        jahr.spendingsTotalYear.spendingsTotalUser.find { it.userId == benId }.userName == 'Ben'   // letzte Mitgliedschaft offen

        and: "I1: Ben trotz drei Mitgliedschaften genau einmal"
        jahr.spendingsTotalYear.spendingsTotalUser*.userId.sort() == [annaId, benId].sort()

        and: "I2: Summe der Zahlungen = Jahressumme"
        jahr.spendingsTotalYear.spendingsTotalUser*.sum.sum() == 68d

        and: "I3/I4: Bens Anteil 29 – mit Doppelzählung des 28.02. wären es 33, mit dem April-Cart 39"
        anteile(jahr.spendingsTotalYear.spendingsTotalUser) == [(annaId): 39d, (benId): 29d]

        and: "Monate absteigend, im März keine Carts"
        jahr.spendingsPerMonth*.month == [5, 4, 2]
        jahr.spendingsPerMonth*.sumTotalMonth == [30d, 10d, 28d]
        // Mai: Ben bezahlt 30, Anteil je 15
        werte(jahr.spendingsPerMonth[0].spendingsMonthlyUser) == [(annaId): [0d, -15d], (benId): [30d, 15d]]
        // Februar: Anna bezahlt 28, Anteil je 10 + 4 = 14 (Ben nicht 10 + 4 + 4)
        werte(jahr.spendingsPerMonth[2].spendingsMonthlyUser) == [(annaId): [28d, 14d], (benId): [0d, -14d]]

        and: "I4: im April (Lücke) fehlt Ben, Anna trägt den Cart allein"
        werte(jahr.spendingsPerMonth[1].spendingsMonthlyUser) == [(annaId): [10d, 0d]]

        and: "I1/I2 je Monat (Mai, April, Februar): jeder Nutzer höchstens einmal, Summe der Zahlungen = Monatssumme"
        jahr.spendingsPerMonth.collect { it.spendingsMonthlyUser*.userId.sort() } ==
                [[annaId, benId].sort(), [annaId], [annaId, benId].sort()]
        jahr.spendingsPerMonth.collect { it.spendingsMonthlyUser*.sum.sum() } == [30d, 10d, 28d]

        and: "Fixture-Abgleich: alle drei Mitgliedschaften sind nach flushAndClear() erhalten"
        def zeitraeume = membershipRepository.findByGroupIdAndUserId(wg.group.id, benId)
                .collect { [DateUtils.toLocalDate(it.membershipStart), it.membershipEnd ? DateUtils.toLocalDate(it.membershipEnd) : null] }
                .sort { it[0] }
        zeitraeume == [[of(2025, 1, 1), of(2025, 2, 28)], [of(2025, 2, 28), of(2025, 3, 31)], [of(2025, 5, 1), null]]
    }

    def "Mitgliedschaft innerhalb eines Monats: Anteile nur an Carts von Eintritts- bis Austrittstag"() {
        given: "Anna (Owner) seit 01.01.2025, Ben nur 10.–20.03.2025"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def (annaId, benId) = [anna, ben]*.id
        def wg = fixtures.group(owner: anna, memberSince: of(2025, 1, 1))
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(of(2025, 3, 10)), membershipEnd: DateUtils.toDate(of(2025, 3, 20)))

        and: "Anna bezahlt alles; Carts am Tag vor dem Eintritt, am Eintritts-, am Austrittstag und am Tag danach"
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 10d, datePurchased: DateUtils.toDate(of(2025, 3, 9)))   // nur Anna
        fixtures.cart(wg, user: anna, amount: 20d, averagePerMember: 10d, datePurchased: DateUtils.toDate(of(2025, 3, 10)))  // Anna, Ben
        fixtures.cart(wg, user: anna, amount: 8d, averagePerMember: 4d, datePurchased: DateUtils.toDate(of(2025, 3, 20)))    // Anna, Ben
        fixtures.cart(wg, user: anna, amount: 6d, averagePerMember: 6d, datePurchased: DateUtils.toDate(of(2025, 3, 21)))    // nur Anna
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {} 2025-03-10 bis 2025-03-20; Anna zahlt am 09., 10., 20., ' +
                '21.03.', wg.group.id, annaId, benId)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then:
        jahr.spendingsTotalYear.sumTotalYear == 44d   // 10 + 20 + 8 + 6
        // Anna: bezahlt 44, Anteil 10 + 10 + 4 + 6 = 30 → diff 14
        // Ben: bezahlt 0, Anteil nur 10.03. und 20.03.: 10 + 4 = 14 → diff −14
        // (bei Beteiligung für den ganzen Mitgliedsmonat wäre Bens Anteil 10 + 10 + 4 + 6 = 30)
        werte(jahr.spendingsTotalYear.spendingsTotalUser) == [(annaId): [44d, 14d], (benId): [0d, -14d]]
        anteile(jahr.spendingsTotalYear.spendingsTotalUser) == [(annaId): 30d, (benId): 14d]

        and: "im März erscheint Ben in der Monatsliste, trägt aber nur die Carts seines Zeitraums"
        jahr.spendingsPerMonth*.month == [3]
        jahr.spendingsPerMonth[0].sumTotalMonth == 44d
        jahr.spendingsPerMonth[0].spendingsMonthlyUser*.userId.sort() == [annaId, benId].sort()
        werte(jahr.spendingsPerMonth[0].spendingsMonthlyUser) == [(annaId): [44d, 14d], (benId): [0d, -14d]]
    }

    def "Rundung (#fall): #betrag auf #mitglieder Mitglieder, Zahler Nr. #zahler → diffs #diffs"() {
        given: "Mitglieder in Anlagereihenfolge, also aufsteigender ID (entscheidet nur bei Gleichstand unter Schuldnern)"
        def nutzer = (0..<mitglieder).collect { fixtures.user() }
        def ids = nutzer*.id
        assert ids == ids.sort(false)
        def wg = fixtures.group(owner: nutzer[0], members: nutzer.drop(1))
        // averagePerMember wie von der App geschrieben (ungerundet); für die Salden ohne Bedeutung
        fixtures.cart(wg, user: nutzer[zahler], amount: betrag, averagePerMember: betrag / mitglieder,
                datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        flushAndClear()
        loginAs(nutzer[0])
        LOG.info('Szenario: Gruppe {}, Mitglieder {}, Zahler {} zahlt {} am {}', wg.group.id, ids, ids[zahler], betrag,
                TestData.REFERENCE_DATE)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(TestData.REFERENCE_DATE.year, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(REFERENCE_DATE.year)', jahr)

        then:
        jahr.spendingsTotalYear.sumTotalYear == betrag
        jahr.spendingsTotalYear.spendingsTotalUser.collectEntries { [it.userId, it.diff] } == [ids, diffs].transpose().collectEntries()
        diffSummeInCent(jahr.spendingsTotalYear.spendingsTotalUser) == 0

        where:
        // B3 (DIV-12): früher Anna +6,67 / andere −3,33 (Summe +0,01) bzw. halber Cent +0,03 / −0,03.
        // Jetzt: exakte Salden, Largest Remainder; bei gleichem Rest zuerst Schuldner, dann kleinere ID.
        fall                  | mitglieder | betrag | zahler || diffs
        'Drittel (AC2)'       | 3          | 10d    | 0      || [6.66d, -3.33d, -3.33d]   // exakt 6,666… / −3,333…
        'Zahler nicht zuerst' | 3          | 10d    | 1      || [-3.33d, 6.66d, -3.33d]   // unabhängig davon, wer zahlt
        '10,01 € auf drei'    | 3          | 10.01d | 0      || [6.67d, -3.33d, -3.34d]   // exakt 6,673… / −3,336…
        'halber Cent'         | 2          | 0.05d  | 0      || [0.02d, -0.02d]           // exakt 0,025 / −0,025
        'ein Cent (AC3)'      | 3          | 0.01d  | 2      || [0d, 0d, 0d]              // exakt −0,003… / −0,003… / 0,006…
        'ohne Rest (AC11)'    | 3          | 15d    | 0      || [10d, -5d, -5d]
    }

    def "Mehrere Einkäufe mit wechselnden Zahlern: Salden je Monat, Jahr und gesamt centgenau mit Summe 0 (AC6, AC9)"() {
        given: "Anna, Ben, Cleo (aufsteigende IDs) seit 01.01.2024"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben, cleo], memberSince: of(2024, 1, 1))

        and: "exakte Anteile je Person: 1000/3, 701/3, 5/3, 2002/3, 100/3 Cent"
        fixtures.cart(wg, user: anna, amount: 10d, datePurchased: DateUtils.toDate(of(2024, 11, 5)))
        fixtures.cart(wg, user: ben, amount: 7.01d, datePurchased: DateUtils.toDate(of(2024, 12, 20)))
        fixtures.cart(wg, user: cleo, amount: 0.05d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        fixtures.cart(wg, user: anna, amount: 20.02d, datePurchased: DateUtils.toDate(of(2025, 3, 15)))
        fixtures.cart(wg, user: ben, amount: 1d, datePurchased: DateUtils.toDate(of(2025, 4, 10)))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {} seit 2024-01-01; fünf Einkäufe ' +
                '2024-11-05 bis 2025-04-10', wg.group.id, annaId, benId, cleoId)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)
        def jahre = [2024, 2025].collect { spendingsOverviewService.getSpendingsForGroupAndYear(it, wg.group.id) }
        LOG.info('spendingsOverviewService.getSpendingsForGroupAndYear(2024, 2025) -> Nutzer [sum, diff] {}',
                jahre.collect { werte(it.spendingsTotalYear.spendingsTotalUser) })

        then: "Gesamt: Anteil je Person 3808/3 = 12,693…; Anna 30,02 → 17,326…, Ben 8,01 → −4,683…, Cleo 0,05 → −12,643…"
        gesamt.spendingsTotalYear.sumTotalYear == 38.08d
        werte(gesamt.spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [30.02d, 17.32d], (benId): [8.01d, -4.68d], (cleoId): [0.05d, -12.64d]]

        and: "2025: Anteil 2107/3 = 7,023…; Anna 12,996…, Ben −6,023…, Cleo −6,973…"
        werte(jahre[1].spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [20.02d, 12.99d], (benId): [1d, -6.02d], (cleoId): [0.05d, -6.97d]]
        jahre[1].spendingsPerMonth*.sumTotalMonth == [1d, 20.07d]

        and: "2025 je Monat: April 100/3 (Anna −0,333…, Ben 0,666…, Cleo −0,333…), März ganzzahlig"
        jahre[1].spendingsPerMonth*.month == [4, 3]
        werte(jahre[1].spendingsPerMonth[0].spendingsMonthlyUser) ==
                [(annaId): [0d, -0.33d], (benId): [1d, 0.66d], (cleoId): [0d, -0.33d]]
        werte(jahre[1].spendingsPerMonth[1].spendingsMonthlyUser) ==
                [(annaId): [20.02d, 13.33d], (benId): [0d, -6.69d], (cleoId): [0.05d, -6.64d]]

        and: "Summe der Salden ist 0 – gesamt, je Jahr (beide Ansichten) und je Monat"
        diffSummeInCent(gesamt.spendingsTotalYear.spendingsTotalUser) == 0
        gesamt.spendingsPerYear.every { diffSummeInCent(it.spendingsYearlyUser) == 0 }
        jahre.every { diffSummeInCent(it.spendingsTotalYear.spendingsTotalUser) == 0 }
        jahre*.spendingsPerMonth.flatten().every { diffSummeInCent(it.spendingsMonthlyUser) == 0 }

        and: "D6: jede Periode wird für sich gerundet – Monate ergeben das Jahr nur bis auf Rundungscent"
        // 2025: Anna Monate 13,33 − 0,33 = 13,00, Jahr 12,99; Ben −6,69 + 0,66 = −6,03, Jahr −6,02
        diffInCentJeNutzer(jahre[1].spendingsPerMonth*.spendingsMonthlyUser.flatten()) ==
                [(annaId): 1300L, (benId): -603L, (cleoId): -697L]
        jahre.every { jahr ->
            def monate = diffInCentJeNutzer(jahr.spendingsPerMonth*.spendingsMonthlyUser.flatten())
            def ganzesJahr = diffInCentJeNutzer(jahr.spendingsTotalYear.spendingsTotalUser)
            ganzesJahr.every { id, cent -> Math.abs(monate[id] - cent) <= jahr.spendingsPerMonth.size() }
        }

        and: "Jahreswerte in Gesamt- und Jahresansicht identisch"
        gesamt.spendingsPerYear.collect { werte(it.spendingsYearlyUser) } ==
                jahre.reverse().collect { werte(it.spendingsTotalYear.spendingsTotalUser) }
    }

    def "Mitgliederwechsel an einem späteren Tag ändern die Aufteilung früherer Einkäufe nicht (AC7)"() {
        given: "Anna und Ben seit 01.01.2025, Cleo tritt am 01.04.2025 bei"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: of(2025, 1, 1))
        fixtures.membership(wg, cleo, membershipStart: DateUtils.toDate(of(2025, 4, 1)))

        and: "Anna bezahlt je 10 € am 01.03. (Anna, Ben: 5,00/5,00) und am 01.05. (alle drei: 3,34/3,33/3,33)"
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 5d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 10d / 3, datePurchased: DateUtils.toDate(of(2025, 5, 1)))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {} ab 2025-04-01; Anna zahlt 10 am 2025-03-01 ' +
                'und am 2025-05-01', wg.group.id, annaId, benId, cleoId)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then: "Anna 20 − 8,34 = 11,66; Ben −8,33; Cleo −3,33"
        werte(jahr.spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [20d, 11.66d], (benId): [0d, -8.33d], (cleoId): [0d, -3.33d]]
        diffSummeInCent(jahr.spendingsTotalYear.spendingsTotalUser) == 0

        and: "März unverändert 5,00/5,00 ohne Cleo, Mai auf drei verteilt"
        jahr.spendingsPerMonth*.month == [5, 3]
        werte(jahr.spendingsPerMonth[0].spendingsMonthlyUser) ==
                [(annaId): [10d, 6.66d], (benId): [0d, -3.33d], (cleoId): [0d, -3.33d]]
        werte(jahr.spendingsPerMonth[1].spendingsMonthlyUser) == [(annaId): [10d, 5d], (benId): [0d, -5d]]
    }

    def "Beitritt am Kauftag nach dem Einkauf beteiligt das neue Mitglied an diesem Einkauf (AC7, Tagessemantik D5)"() {
        given: "Anna und Ben seit 01.01.2025; Cleo tritt am 10.03.2025 um 18 Uhr bei"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben], memberSince: of(2025, 1, 1))
        fixtures.membership(wg, cleo, membershipStart: at(of(2025, 3, 10), 18))

        and: "Anna bezahlt am 10.03. 10 € – erfasst, als Cleo noch nicht Mitglied war"
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 5d, datePurchased: DateUtils.toDate(of(2025, 3, 10)))
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {} ab 2025-03-10 18 Uhr; Anna zahlt 10 am 2025-03-10',
                wg.group.id, annaId, benId, cleoId)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then: "Mitgliedschaft zählt tagesgenau inklusive Eintrittstag (gewollt, D5): Aufteilung auf drei"
        werte(jahr.spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [10d, 6.66d], (benId): [0d, -3.33d], (cleoId): [0d, -3.33d]]
        diffSummeInCent(jahr.spendingsTotalYear.spendingsTotalUser) == 0
    }

    def "Altdaten: averagePerMember wird ignoriert, Salden centgenau aus Betrag und Historie (AC8)"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben, cleo])

        and: "ein ungerundeter Altwert (so wie ihn die NUMERIC-Spalte für 10/3 speichert) und ein bewusst falscher Wert"
        def c1 = fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 3.33333333333333d, datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        def c2 = fixtures.cart(wg, user: ben, amount: 5d, averagePerMember: 99d, datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        def (c1Id, c2Id) = [c1, c2]*.id
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {}; Einkauf {} Anna 10 (averagePerMember 3.333…), ' +
                'Einkauf {} Ben 5 (averagePerMember 99)', wg.group.id, annaId, benId, cleoId, c1Id, c2Id)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)

        then: "exakter Anteil je Person (10 + 5) / 3 = 5,00: Anna +5, Ben 0, Cleo −5"
        werte(gesamt.spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [10d, 5d], (benId): [5d, 0d], (cleoId): [0d, -5d]]
        diffSummeInCent(gesamt.spendingsTotalYear.spendingsTotalUser) == 0

        and: "Beträge und Altwerte bleiben unverändert gespeichert"
        [c1Id, c2Id].collect { cartRepository.findById(it).get() }.collect { [it.amount, it.averagePerMember] } ==
                [[10d, 3.33333333333333d], [5d, 99d]]
    }

    def "Ausgleichszahlungen in Höhe der Salden gleichen centgenau aus, Unbeteiligte bleiben unverändert (AC10, D4)"() {
        given: "Anna (Owner), Ben, Cleo; Anna bezahlt 10 € → Anna +6,66, Ben −3,33, Cleo −3,33"
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben, cleo])
        def tag = DateUtils.toDate(TestData.REFERENCE_DATE)
        fixtures.cart(wg, user: anna, amount: 10d, averagePerMember: 10d / 3, datePurchased: tag)
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {}; Anna zahlt 10 am {}', wg.group.id, annaId, benId,
                cleoId, TestData.REFERENCE_DATE)

        when: "Ben zahlt Cleo 1,00 € (nicht durch drei teilbar)"
        zahlung(benId, cleoId, 1d, wg, tag)

        then: "nur Ben und Cleo verschieben sich um genau 1,00 €"
        diffs(annaId, wg) == [(annaId): 6.66d, (benId): -2.33d, (cleoId): -4.33d]

        when: "Ben und Cleo zahlen Anna ihren angezeigten Saldo"
        zahlung(benId, annaId, 2.33d, wg, tag)
        zahlung(cleoId, annaId, 4.33d, wg, tag)

        then: "alle Salden exakt 0, keine Rest-Cent"
        diffs(annaId, wg) == [(annaId): 0d, (benId): 0d, (cleoId): 0d]

        and: "die Auswertung verändert keine Carts: Einkauf plus drei Zahlungen mit je Sender- und Empfänger-Cart"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)*.amount.sort() ==
                [-4.33d, -2.33d, -1d, 1d, 2.33d, 4.33d, 10d]
    }

    def "Beträge mit mehr als zwei Nachkommastellen werden je Cart auf Cent gerundet (D3)"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def ben = fixtures.user(name: 'Ben')
        def cleo = fixtures.user(name: 'Cleo')
        def (annaId, benId, cleoId) = [anna, ben, cleo]*.id
        assert annaId < benId && benId < cleoId
        def wg = fixtures.group(owner: anna, members: [ben, cleo], memberSince: of(2025, 1, 1))

        and: "in Cent (HALF_UP je Cart): 1, 1, −1, 944 – exakter Anteil je Person gesamt 945/3 = 315"
        def carts = [
                fixtures.cart(wg, user: anna, amount: 0.005d, datePurchased: DateUtils.toDate(of(2025, 3, 1))),
                fixtures.cart(wg, user: anna, amount: 0.005d, datePurchased: DateUtils.toDate(of(2025, 3, 2))),
                fixtures.cart(wg, user: ben, amount: -0.005d, datePurchased: DateUtils.toDate(of(2025, 4, 1))),
                fixtures.cart(wg, user: cleo, amount: 9.43668881979196d, datePurchased: DateUtils.toDate(of(2025, 4, 2)))]
        def cartIds = carts*.id
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Anna {} (Owner), Ben {}, Cleo {}; Einkäufe {} mit 0.005, 0.005, -0.005, 9.4366…',
                wg.group.id, annaId, benId, cleoId, cartIds)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then: "Anna 0,02 − 3,15 = −3,13; Ben −0,01 − 3,15 = −3,16; Cleo 9,44 − 3,15 = 6,29 (Betrag erst nach Addition runden gäbe Anna 0,01)"
        def erwartet = [(annaId): [0.02d, -3.13d], (benId): [-0.01d, -3.16d], (cleoId): [9.44d, 6.29d]]
        werte(gesamt.spendingsTotalYear.spendingsTotalUser) == erwartet
        werte(gesamt.spendingsPerYear[0].spendingsYearlyUser) == erwartet
        werte(jahr.spendingsTotalYear.spendingsTotalUser) == erwartet
        diffSummeInCent(jahr.spendingsTotalYear.spendingsTotalUser) == 0

        and: "Summenfelder aus den je Cart gerundeten Beträgen"
        gesamt.spendingsTotalYear.sumTotalYear == 9.45d
        gesamt.spendingsPerYear*.sumTotalYear == [9.45d]
        jahr.spendingsTotalYear.sumTotalYear == 9.45d
        jahr.spendingsPerMonth*.month == [4, 3]
        jahr.spendingsPerMonth*.sumTotalMonth == [9.43d, 0.02d]
        // April exakt: Anna −314,33, Ben −315,33, Cleo 629,67 Cent; März: Anna 1,33, Ben −0,67, Cleo −0,67 Cent
        werte(jahr.spendingsPerMonth[0].spendingsMonthlyUser) ==
                [(annaId): [0d, -3.14d], (benId): [-0.01d, -3.15d], (cleoId): [9.44d, 6.29d]]
        werte(jahr.spendingsPerMonth[1].spendingsMonthlyUser) ==
                [(annaId): [0.02d, 0.01d], (benId): [0d, 0d], (cleoId): [0d, -0.01d]]

        and: "gespeicherte Beträge unverändert"
        cartIds.collect { cartRepository.findById(it).get().amount } == [0.005d, 0.005d, -0.005d, 9.43668881979196d]
    }

    def "Gesamtsumme über alle Jahre und Monatssumme sind centgenau: 0,10 + 0,20 ergibt 0,3 (DIV-29)"() {
        given:
        def wg = fixtures.group(memberSince: of(2025, 1, 1))
        fixtures.cart(wg, amount: 0.1d, datePurchased: DateUtils.toDate(of(2025, 3, 1)))
        fixtures.cart(wg, amount: 0.2d, datePurchased: DateUtils.toDate(of(2025, 3, 2)))
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {}; Einkäufe 0.10 am 2025-03-01, 0.20 am 2025-03-02', wg.group.id, wg.owner.id)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then: "früher 0.30000000000000004"
        gesamt.spendingsTotalYear.sumTotalYear == 0.3d
        jahr.spendingsPerMonth*.sumTotalMonth == [0.3d]
    }

    def "Ohne Carts ist die Gesamtübersicht leer"() {
        given:
        def wg = fixtures.group()
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {}, keine Einkäufe', wg.group.id, wg.owner.id)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndAllYears', gesamt)

        then:
        gesamt.spendingsTotalYear.sumTotalYear == 0d
        gesamt.spendingsTotalYear.spendingsTotalUser == []
        gesamt.spendingsPerYear == []
        gesamt.availableYears == []
    }

    def "Ein Jahr ohne Carts liefert keine Nutzer und keine Monate"() {
        given:
        def wg = fixtures.group(memberSince: of(2024, 1, 1))
        fixtures.cart(wg, amount: 12.5d, averagePerMember: 12.5d, datePurchased: DateUtils.toDate(of(2024, 3, 1)))
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf 12.50 am 2024-03-01', wg.group.id, wg.owner.id)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)
        logUebersicht('spendingsOverviewService.getSpendingsForGroupAndYear(2025)', jahr)

        then:
        jahr.spendingsTotalYear.sumTotalYear == 0d
        jahr.spendingsTotalYear.spendingsTotalUser == []
        jahr.spendingsPerMonth == []
        jahr.availableYears == [2024]
    }

    /** Ergebnis eines Übersichts-Aufrufs (INFO): Summe und [sum, diff] je Nutzer. */
    private static void logUebersicht(String aufruf, overview) {
        LOG.info('{} -> sumTotalYear {}, Nutzer [sum, diff] {}', aufruf, overview.spendingsTotalYear.sumTotalYear,
                werte(overview.spendingsTotalYear.spendingsTotalUser))
    }

    private static Map<Long, List<Double>> werte(List<SpendingsOverviewUserDto> users) {
        users.collectEntries { [it.userId, [it.sum, it.diff]] }
    }

    private static Map<Long, Double> anteile(List<SpendingsOverviewUserDto> users) {
        users.collectEntries { [it.userId, it.sum - it.diff] }
    }

    /** Summe der Salden in Cent – exakt, ohne Double-Addition. */
    private static long diffSummeInCent(List<SpendingsOverviewUserDto> users) {
        users.sum(0L) { CostShareCalculator.toCents(it.diff) } as long
    }

    /** Salden je Nutzer in Cent, über mehrere Listen (z. B. alle Monate eines Jahres) aufaddiert. */
    private static Map<Long, Long> diffInCentJeNutzer(List<SpendingsOverviewUserDto> users) {
        users.groupBy { it.userId }.collectEntries { id, eintraege -> [id, diffSummeInCent(eintraege)] }
    }

    /** Ausgleichszahlung über CartService, wie sie die App anlegt (Sender- und Empfänger-Cart). */
    private void zahlung(Long vonId, Long anId, Double betrag, GroupScenario wg, Date tag) {
        loginAs(userRepository.findById(vonId).get())
        def an = userRepository.findById(anId).get()
        cartService.addSettlementPayment(new SettlementPaymentDto(betrag, wg.group.id, new UserDto(an), tag))
        flushAndClear()
        LOG.info('cartService.addSettlementPayment(User {} an User {}, {})', vonId, anId, betrag)
    }

    private Map<Long, Double> diffs(Long betrachterId, GroupScenario wg) {
        loginAs(userRepository.findById(betrachterId).get())
        spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
                .spendingsTotalYear.spendingsTotalUser.collectEntries { [it.userId, it.diff] }
    }

    /** Zeitpunkt mit Uhrzeit, wie ihn GroupMembershipHistoryService beim Ein- und Austritt mit new Date() schreibt. */
    private static Date at(LocalDate day, int hour) {
        Date.from(DateUtils.toDate(day).toInstant().plus(Duration.ofHours(hour)))
    }
}
