package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

/**
 * Centgenaue Salden (DIV-38, Entscheidung D6): Anteile exakt als Bruch zum gemeinsamen Nenner, gerundet wird erst
 * der Saldo nach dem Largest-Remainder-Verfahren. Salden werden als Zähler zum Nenner übergeben, z. B. Nenner 3 und
 * Zähler 2000 = 666,67 Cent.
 */
class CostShareCalculatorSpec extends Specification {

    def "#fall: Salden #exakt / #nenner werden zu #erwartet Cent"() {
        when:
        def gerundet = CostShareCalculator.roundBalances(zaehler(exakt), nenner as BigInteger)

        then:
        gerundet == erwartet
        gerundet.values().sum() == 0L

        where:
        fall                                     | nenner | exakt                          || erwartet
        // 10,00 € auf drei, 1 zahlt: 666,67 / −333,33 / −333,33 → Schuldner zuerst, der Zahler trägt den Rest
        'AC2: 10,00 € auf drei, 1 zahlt'         | 3      | [1L: 2000, 2L: -1000, 3L: -1000] || [1L: 666L, 2L: -333L, 3L: -333L]
        'AC2 mit Zahler in der Mitte'            | 3      | [1L: -1000, 2L: 2000, 3L: -1000] || [1L: -333L, 2L: 666L, 3L: -333L]
        // 10,01 €: 667,33 / −333,67 / −333,67 → ein Cent fehlt, Gleichstand unter Schuldnern → kleinere ID
        '10,01 € auf drei, 1 zahlt'              | 3      | [1L: 2002, 2L: -1001, 3L: -1001] || [1L: 667L, 2L: -333L, 3L: -334L]
        // 0,01 € auf drei, 3 zahlt: −0,33 / −0,33 / 0,67 → Schuldner auf 0, Zahler trägt den Cent
        'AC3: 0,01 € auf drei, 3 zahlt'          | 3      | [1L: -1, 2L: -1, 3L: 2]          || [1L: 0L, 2L: 0L, 3L: 0L]
        // 10,01 € auf zwei, 1 zahlt: 500,5 / −500,5 → Anteile 5,01 (Zahler) und 5,00
        'AC4: 10,01 € auf zwei, 1 zahlt'         | 2      | [1L: 1001, 2L: -1001]            || [1L: 500L, 2L: -500L]
        // ohne Gleichstand entscheidet der größere Rest: 0,33 / 0,67 / −1
        'größerer Rest vor Schuldner und ID'     | 3      | [1L: 1, 2L: 2, 3L: -3]           || [1L: 0L, 2L: 1L, 3L: -1L]
        'ganzzahlige Salden bleiben (AC11)'      | 1      | [1L: 1000, 2L: -500, 3L: -500]   || [1L: 1000L, 2L: -500L, 3L: -500L]
    }

    def "Summe der gerundeten Salden ist die exakte Summe, auch wenn sie nicht 0 ist (Einkauf ohne Beteiligte)"() {
        expect: "1000 Cent bezahlt, niemand beteiligt: exakt 3000/3"
        CostShareCalculator.roundBalances(zaehler([1L: 3000, 2L: 0]), 3G) == [1L: 1000L, 2L: 0L]
    }

    def "AC5: Jede Reihenfolge der Salden ergibt dieselbe Rundung"() {
        given:
        def salden = [[11L, 2000], [4L, -1000], [27L, -1000], [9L, 0]]

        when:
        def ergebnisse = salden.permutations().collect { liste ->
            CostShareCalculator.roundBalances(zaehler(liste.collectEntries()), 3G)
        }*.sort().unique()

        then:
        ergebnisse == [[4L: -333L, 9L: 0L, 11L: 666L, 27L: -333L]]
    }

    def "Jeder Saldo weicht um weniger als einen Cent ab, die Summe bleibt erhalten (#nenner)"() {
        given:
        def random = new Random(nenner)
        def exakt = (1L..7L).collectEntries { [it, random.nextInt(2_000_001) - 1_000_000] }
        exakt[8L] = -exakt.values().sum()   // Periode vollständig: Summe 0

        when:
        def gerundet = CostShareCalculator.roundBalances(zaehler(exakt), nenner as BigInteger)

        then:
        gerundet.values().sum() == 0L
        exakt.every { id, wert -> Math.abs(gerundet[id] * nenner - wert) < nenner }

        where:
        nenner << [2, 3, 12, 60, 2520]
    }

    def "Gemeinsamer Nenner von #anzahlen ist #erwartet"() {
        expect:
        CostShareCalculator.commonDenominator(anzahlen) == erwartet as BigInteger

        where:
        anzahlen        || erwartet
        [3, 2, 3]       || 6
        [1, 2, 3, 4, 5] || 60
        [0, 4]          || 4   // Einkauf ohne Beteiligte zählt nicht
        []              || 1
    }

    def "Anteil von #cent Cent auf #anzahl Teilnehmer zum Nenner #nenner ist #erwartet"() {
        expect:
        CostShareCalculator.shareNumerator(cent, anzahl, nenner as BigInteger) == erwartet as BigInteger

        where:
        cent  | anzahl | nenner || erwartet
        1000  | 3      | 6      || 2000    // 333,33 Cent
        -1000 | 2      | 6      || -3000   // −500 Cent
    }

    def "toCents(#betrag) == #cents"() {
        expect:
        CostShareCalculator.toCents(betrag) == cents

        where:
        betrag            || cents
        10d               || 1000L
        0.1d + 0.2d       || 30L    // Binärartefakt 0.30000000000000004
        9.43668881979196d || 944L   // mehr als zwei Nachkommastellen (D3)
        0.005d            || 1L     // HALF_UP
        -0.005d           || -1L    // HALF_UP vom Nullpunkt weg
        -3.33d            || -333L
    }

    def "toEuro(#cents) == #euro"() {
        expect:
        CostShareCalculator.toEuro(cents) == euro

        where:
        cents || euro
        666L  || 6.66d
        -333L || -3.33d
        30L   || 0.3d
        0L    || 0d
    }

    private static Map<Long, BigInteger> zaehler(Map<Long, Integer> werte) {
        werte.collectEntries { id, wert -> [id, wert as BigInteger] } as LinkedHashMap
    }
}
