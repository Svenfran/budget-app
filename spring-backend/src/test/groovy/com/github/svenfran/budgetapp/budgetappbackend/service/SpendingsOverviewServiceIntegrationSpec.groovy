package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewUserDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.beans.factory.annotation.Autowired

import java.time.Duration
import java.time.LocalDate

import static java.time.LocalDate.of

/**
 * Jahres- und Monatswerte der Ausgabenübersicht mit Ein- und Austritten (Ist-Verhalten).
 *
 * - averagePerMember wird gesetzt, wie die App ihn beim Anlegen berechnet: Betrag / Mitglieder am Kaufdatum.
 * - Nutzerwerte werden als [userId: [sum, diff]] verglichen; die Reihenfolge der Nutzer ist nicht garantiert.
 * - Anteil eines Nutzers = sum - diff.
 * - Ungerundete Summenfelder (sumTotalYear über alle Jahre, sumTotalMonth) nur mit exakt darstellbaren Beträgen.
 * - Bedingungen enthalten nur IDs, keine Entities: Spock rendert bei einem Fehlschlag jeden Teilausdruck, und
 *   IDs statt Entities halten die Fehlermeldung lesbar.
 */
class SpendingsOverviewServiceIntegrationSpec extends IntegrationSpec {

    @Autowired SpendingsOverviewService spendingsOverviewService
    @Autowired GroupRepository groupRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository

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

        when:
        def jahr2024 = spendingsOverviewService.getSpendingsForGroupAndYear(2024, wg.group.id)

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

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)

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

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)

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

    def "Rundung (#fall): Anna bezahlt #betrag für #mitglieder Mitglieder, diff #annaDiff, andere #andereDiff"() {
        given:
        def anna = fixtures.user(name: 'Anna')
        def andere = (1..<mitglieder).collect { fixtures.user() }
        def (annaId, andereIds) = [anna.id, andere*.id]
        def wg = fixtures.group(owner: anna, members: andere)
        fixtures.cart(wg, user: anna, amount: betrag, averagePerMember: betrag / mitglieder,
                datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        flushAndClear()
        loginAs(anna)

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(TestData.REFERENCE_DATE.year, wg.group.id)

        then:
        jahr.spendingsTotalYear.sumTotalYear == betrag
        werte(jahr.spendingsTotalYear.spendingsTotalUser) ==
                [(annaId): [betrag, annaDiff]] + andereIds.collectEntries { [it, [0d, andereDiff]] }

        where:
        // Drittel: Ø 10/3 = 3,333…; Anna 10 − 3,333… = 6,666… → 6,67; andere −3,33. Die gerundeten diffs ergeben
        // zusammen +0,01 (Ist-Verhalten; ob ein Rest-Cent-Ausgleich gewollt ist, ist fachlich offen – B3 im Plan).
        // Halber Cent: Ø 0,05/2 = 0,025; HALF_UP rundet vom Nullpunkt weg: 0,025 → 0,03, −0,025 → −0,03.
        fall          | mitglieder | betrag || annaDiff | andereDiff
        'Drittel'     | 3          | 10d    || 6.67d    | -3.33d
        'halber Cent' | 2          | 0.05d  || 0.03d    | -0.03d
    }

    def "Ohne Carts ist die Gesamtübersicht leer"() {
        given:
        def wg = fixtures.group()
        flushAndClear()
        loginAs(wg.owner)

        when:
        def gesamt = spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)

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

        when:
        def jahr = spendingsOverviewService.getSpendingsForGroupAndYear(2025, wg.group.id)

        then:
        jahr.spendingsTotalYear.sumTotalYear == 0d
        jahr.spendingsTotalYear.spendingsTotalUser == []
        jahr.spendingsPerMonth == []
        jahr.availableYears == [2024]
    }

    private static Map<Long, List<Double>> werte(List<SpendingsOverviewUserDto> users) {
        users.collectEntries { [it.userId, [it.sum, it.diff]] }
    }

    private static Map<Long, Double> anteile(List<SpendingsOverviewUserDto> users) {
        users.collectEntries { [it.userId, it.sum - it.diff] }
    }

    /** Zeitpunkt mit Uhrzeit, wie ihn GroupMembershipHistoryService beim Ein- und Austritt mit new Date() schreibt. */
    private static Date at(LocalDate day, int hour) {
        Date.from(DateUtils.toDate(day).toInstant().plus(Duration.ofHours(hour)))
    }
}
