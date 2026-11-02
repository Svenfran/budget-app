package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.helper.StatisticsCalculator.CategoryEntry
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod.*

/**
 * Statistikseite (DIV-63): Gesamtausgaben, Von dir bezahlt, Zeitreihe (ISO-Kalenderwochen, Monate, Jahre) und
 * Kategorien in Cent. Kategorien werden gruppenübergreifend nach Name zusammengefasst (K2), Ausgleichszahlungen
 * eingerechnet und in einer Zeile gekennzeichnet (K1). Zeitreihe und Kategorien ergeben zusammen jeweils "Von dir bezahlt".
 */
class StatisticsCalculatorSpec extends Specification {

    static final Set<String> SETTLEMENT = ['Ausgleichszahlung', 'Repayment', 'Repago']
            .collect { StatisticsCalculator.normalize(it) } as Set

    def anna = TestData.user(id: 1L)
    def ben = TestData.user(id: 2L)
    def haushalt = TestData.group(id: 10L)
    def urlaub = TestData.group(id: 20L)
    def lebensmittel = TestData.category(name: 'Lebensmittel', group: haushalt)

    def "AC5: Gesamtausgaben 1.000,00 €, davon 600,00 € vom Nutzer bezahlt; Carts anderer Jahre zählen nicht"() {
        given:
        def carts = [einkauf(anna, 600.0, LocalDate.of(2025, 3, 1)),
                     einkauf(ben, 400.0, LocalDate.of(2025, 7, 1)),
                     einkauf(anna, 99.0, LocalDate.of(2024, 12, 31)),
                     einkauf(ben, 99.0, LocalDate.of(2026, 1, 1))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2026, 2025, 2024], SETTLEMENT)

        then:
        result.totalCents() == 100000L
        result.paidCents() == 60000L
        result.categories() == [new CategoryEntry('Lebensmittel', 60000L, false)]
    }

    def "AC7: Jahresansicht hat 12 Monate, leere Monate sind 0, die Summe ist Von dir bezahlt"() {
        given:
        def carts = [einkauf(anna, 10.0, LocalDate.of(2025, 1, 31)),
                     einkauf(anna, 20.5, LocalDate.of(2025, 3, 1)),
                     einkauf(anna, 5.0, LocalDate.of(2025, 3, 31)),
                     einkauf(anna, 7.0, LocalDate.of(2025, 12, 31)),
                     einkauf(ben, 100.0, LocalDate.of(2025, 2, 1))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2025], SETTLEMENT)

        then:
        result.timeline()*.month == (1..12).toList()
        result.timeline()*.year.unique() == [2025]
        result.timeline()*.amountCents() == [1000L, 0L, 2550L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 700L]
        result.timeline()*.amountCents().sum() == result.paidCents()
        result.paidCents() == 4250L
        result.totalCents() == 14250L
    }

    def "Monatsansicht #jahr-#monat: genau die ISO-Kalenderwochen, die den Monat berühren, auf den Monat beschnitten"() {
        when:
        def result = StatisticsCalculator.calculate([], anna.id, MONTH, jahr, monat, [], SETTLEMENT)

        then:
        result.timeline().collect { [it.year(), it.week(), it.from().toString(), it.to().toString()] } == wochen
        result.timeline()*.month.unique() == [null]

        where:
        jahr | monat || wochen
        // 1.1.2027 ist ein Freitag: 28.12.2026–3.1.2027 ist KW 53/2026 (2026 hat 53 Wochen)
        2027 | 1     || [[2026, 53, '2027-01-01', '2027-01-03'], [2027, 1, '2027-01-04', '2027-01-10'],
                         [2027, 2, '2027-01-11', '2027-01-17'], [2027, 3, '2027-01-18', '2027-01-24'],
                         [2027, 4, '2027-01-25', '2027-01-31']]
        // 1.3.2027 ist ein Montag
        2027 | 3     || [[2027, 9, '2027-03-01', '2027-03-07'], [2027, 10, '2027-03-08', '2027-03-14'],
                         [2027, 11, '2027-03-15', '2027-03-21'], [2027, 12, '2027-03-22', '2027-03-28'],
                         [2027, 13, '2027-03-29', '2027-03-31']]
    }

    def "AC6: Monatsansicht enthält nur Ausgaben des Monats, die Summe der Wochen ist Von dir bezahlt"() {
        given:
        def carts = [einkauf(anna, 1.0, LocalDate.of(2026, 12, 31)),
                     einkauf(anna, 2.0, LocalDate.of(2027, 1, 1)),
                     einkauf(anna, 3.0, LocalDate.of(2027, 1, 3)),
                     einkauf(anna, 4.0, LocalDate.of(2027, 1, 4)),
                     einkauf(anna, 5.0, LocalDate.of(2027, 1, 31)),
                     einkauf(anna, 6.0, LocalDate.of(2027, 2, 1)),
                     einkauf(ben, 10.0, LocalDate.of(2027, 1, 20))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, MONTH, 2027, 1, [2027, 2026], SETTLEMENT)

        then:
        result.timeline()*.amountCents() == [500L, 400L, 0L, 0L, 500L]
        result.timeline()*.amountCents().sum() == result.paidCents()
        result.paidCents() == 1400L
        result.totalCents() == 2400L
    }

    def "AC8: Gesamter Zeitraum hat einen Balken je Jahr vom ersten bis zum letzten Jahr mit Carts, Lücken sind 0"() {
        given:
        def carts = [einkauf(anna, 10.0, LocalDate.of(2023, 5, 1)),
                     einkauf(anna, 2.5, LocalDate.of(2025, 1, 1)),
                     einkauf(ben, 30.0, LocalDate.of(2024, 1, 1))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, ALL, null, null, [2025, 2024, 2023], SETTLEMENT)

        then:
        result.timeline()*.year == [2023, 2024, 2025]
        result.timeline()*.amountCents() == [1000L, 0L, 250L]
        result.timeline()*.amountCents().sum() == result.paidCents()
        result.totalCents() == 4250L
    }

    def "Ohne Carts sind alle Summen 0, die Kategorien leer und die Zeitreihe bei ALL leer"() {
        when:
        def result = StatisticsCalculator.calculate([], anna.id, ALL, null, null, [], SETTLEMENT)

        then:
        result.totalCents() == 0L
        result.paidCents() == 0L
        result.paidCartCount() == 0L
        result.timeline() == []
        result.categories() == []
    }

    def "Nur andere Mitglieder haben bezahlt: Gesamtausgaben sichtbar, Zeitreihe nur 0, keine Kategorien"() {
        given:
        def carts = [einkauf(ben, 80.0, LocalDate.of(2025, 4, 1))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2025], SETTLEMENT)

        then:
        result.totalCents() == 8000L
        result.paidCents() == 0L
        result.timeline()*.amountCents().unique() == [0L]
        result.categories() == []
    }

    def "K2/AC9: Kategorien werden gruppenübergreifend nach Name zusammengefasst, 0-Zeilen entfallen, Sortierung Betrag absteigend, dann Name"() {
        given: "die Urlaub-Gruppe hat die kleinere Cart-ID; der Anzeigename kommt trotzdem aus der kleineren Gruppen-ID"
        def urlaubLebensmittel = TestData.category(name: ' LEBENSMITTEL ', group: urlaub)
        def carts = [einkauf(anna, 5.0, LocalDate.of(2025, 2, 1), urlaubLebensmittel, urlaub),
                     einkauf(anna, 10.0, LocalDate.of(2025, 1, 1)),
                     einkauf(anna, 7.0, LocalDate.of(2025, 1, 1), TestData.category(name: 'Groceries', group: urlaub), urlaub),
                     einkauf(anna, 7.0, LocalDate.of(2025, 1, 1), TestData.category(name: 'bad', group: haushalt)),
                     einkauf(anna, 3.0, LocalDate.of(2025, 1, 1), TestData.category(name: 'Haushalt', group: haushalt)),
                     einkauf(anna, -3.0, LocalDate.of(2025, 1, 2), TestData.category(name: 'haushalt', group: urlaub), urlaub),
                     einkauf(ben, 50.0, LocalDate.of(2025, 1, 1), TestData.category(name: 'Technik', group: haushalt))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2025], SETTLEMENT)

        then:
        result.categories() == [new CategoryEntry('Lebensmittel', 1500L, false),
                                new CategoryEntry('bad', 700L, false),
                                new CategoryEntry('Groceries', 700L, false)]
        result.categories()*.amountCents().sum() == result.paidCents()
    }

    def "K1: Ausgleichszahlungen zählen in Von dir bezahlt und Zeitreihe; alle Sprachen in einer Zeile, negative Kategorien bleiben"() {
        given:
        def ausgleich = TestData.category(name: 'Ausgleichszahlung', group: haushalt)
        def repayment = TestData.category(name: 'repayment ', group: urlaub)
        def carts = [einkauf(anna, 100.0, LocalDate.of(2025, 1, 10)),
                     einkauf(anna, 50.0, LocalDate.of(2025, 2, 1), ausgleich),
                     einkauf(ben, -50.0, LocalDate.of(2025, 2, 1), ausgleich),
                     einkauf(anna, -20.0, LocalDate.of(2025, 3, 1), repayment, urlaub),
                     einkauf(anna, -10.0, LocalDate.of(2025, 4, 1), TestData.category(name: 'Erstattung', group: haushalt))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2025], SETTLEMENT)

        then:
        result.paidCents() == 12000L
        result.totalCents() == 7000L
        result.timeline()*.amountCents().take(4) == [10000L, 5000L, -2000L, -1000L]
        result.categories() == [new CategoryEntry('Lebensmittel', 10000L, false),
                                new CategoryEntry('Ausgleichszahlung', 3000L, true),
                                new CategoryEntry('Erstattung', -1000L, false)]
        result.categories()*.amountCents().sum() == result.paidCents()
        result.timeline()*.amountCents().sum() == result.paidCents()
    }

    def "Bezahlte Einkäufe #period: eigene Carts im Zeitraum aus allen übergebenen Gruppen, ohne Ausgleichszahlungen"() {
        given: "Anna: 3 Einkäufe im März 2025 (davon einer in Urlaub, einer negativ), je einer im Juli 2025 und 2024, Ausgleichszahlungen in allen Sprachen"
        def ausgleich = TestData.category(name: 'Ausgleichszahlung', group: haushalt)
        def repayment = TestData.category(name: ' Repayment', group: urlaub)
        def carts = [einkauf(anna, 10.0, LocalDate.of(2025, 3, 1)),
                     einkauf(anna, 4.0, LocalDate.of(2025, 3, 2), TestData.category(name: 'Lebensmittel', group: urlaub), urlaub),
                     einkauf(anna, -5.0, LocalDate.of(2025, 3, 31), TestData.category(name: 'Erstattung', group: haushalt)),
                     einkauf(anna, 20.0, LocalDate.of(2025, 7, 1)),
                     einkauf(anna, 30.0, LocalDate.of(2024, 12, 31)),
                     einkauf(anna, 50.0, LocalDate.of(2025, 3, 15), ausgleich),
                     einkauf(ben, -50.0, LocalDate.of(2025, 3, 15), ausgleich),
                     einkauf(anna, -20.0, LocalDate.of(2025, 8, 1), repayment, urlaub),
                     einkauf(ben, 99.0, LocalDate.of(2025, 3, 10))]

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, period, year, month, [2025, 2024], SETTLEMENT)

        then:
        result.paidCartCount() == anzahl
        result.paidCents() == bezahlt

        where: "Von dir bezahlt enthält die Ausgleichszahlungen weiterhin"
        period | year | month || anzahl | bezahlt
        MONTH  | 2025 | 3     || 3L     | 5900L
        YEAR   | 2025 | null  || 4L     | 5900L
        ALL    | null | null  || 5L     | 8900L
    }

    def "Rundung je Cart auf Cent (HALF_UP): #betraege ergeben #cent Cent"() {
        given:
        def carts = betraege.collect { einkauf(anna, it as Double, LocalDate.of(2025, 1, 1)) }

        when:
        def result = StatisticsCalculator.calculate(carts, anna.id, YEAR, 2025, null, [2025], SETTLEMENT)

        then:
        result.paidCents() == cent
        result.totalCents() == cent
        result.timeline()[0].amountCents() == cent
        result.categories()*.amountCents() == [cent]

        where:
        betraege            || cent
        [0.1d, 0.2d]        || 30L
        [10.005d, 0.004d]   || 1001L
        [-1.005d, 2.0d]     || 99L
    }

    /** Cart mit fester ID; Kategorie und Gruppe standardmäßig Lebensmittel/Haushalt. */
    private Cart einkauf(User zahler, Double betrag, LocalDate datum, Category kategorie = lebensmittel, def gruppe = haushalt) {
        TestData.cart(id: TestData.nextId(), user: zahler, amount: betrag, datePurchased: DateUtils.toDate(datum),
                category: kategorie, group: gruppe)
    }
}
