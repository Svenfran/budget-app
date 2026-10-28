package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.entity.ShoppingPurchase
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Einkaufsvorschläge (DIV-23): Rangfolge Häufigkeit → letzter Kauf → Name, Anzeigename = jüngste Schreibweise,
 * vorhandene Listeneinträge und namenlose Käufe ausgeschlossen, Limit.
 */
class ShoppingSuggestionRankerSpec extends Specification {

    def "Häufiger gekaufte Artikel stehen vorn, bei gleicher Anzahl der zuletzt gekaufte, danach der Name"() {
        given:
        def kaeufe = [
                kauf(1, 'Brot', 10), kauf(2, 'Brot', 11),
                kauf(3, 'Milch', 1), kauf(4, 'Milch', 2), kauf(5, 'Milch', 3),
                kauf(6, 'Kaffee', 20),
                kauf(7, 'Zucker', 5), kauf(8, 'Apfel', 5)]

        expect:
        ShoppingSuggestionRanker.rank(kaeufe, [], 15) == ['Milch', 'Brot', 'Kaffee', 'Apfel', 'Zucker']
    }

    def "Schreibweisen werden zusammengefasst und zählen gemeinsam; angezeigt wird die des jüngsten Kaufs ohne Leerzeichen außen"() {
        given:
        def kaeufe = [kauf(1, 'milch', 1), kauf(2, ' MILCH ', 3), kauf(3, 'Milch', 2), kauf(4, 'Brot', 4), kauf(5, 'Brot', 5)]

        expect:
        ShoppingSuggestionRanker.rank(kaeufe, [], 15) == ['MILCH', 'Brot']
    }

    def "Gleicher Zeitpunkt, unterschiedliche Schreibweise: die höhere Ereignis-ID bestimmt den Anzeigenamen (#reihenfolge)"() {
        given:
        def kaeufe = [kauf(7, 'Milch', 1), kauf(9, 'MILCH', 1), kauf(8, 'milch', 1)]

        expect:
        ShoppingSuggestionRanker.rank(reihenfolge == 'aufsteigend' ? kaeufe : kaeufe.reverse(), [], 15) == ['MILCH']

        where:
        reihenfolge << ['aufsteigend', 'umgekehrt']
    }

    def "Artikel der Liste (offen oder erledigt, beliebige Schreibweise) werden nicht vorgeschlagen; namenlose Listeneinträge schließen nichts aus"() {
        given:
        def kaeufe = [kauf(1, 'Milch', 1), kauf(2, 'Brot', 2), kauf(3, 'Kaffee', 3)]

        expect:
        ShoppingSuggestionRanker.rank(kaeufe, [' milch', 'KAFFEE', null, ''], 15) == ['Brot']
    }

    def "Käufe ohne Namen werden ignoriert"() {
        given:
        def kaeufe = [kauf(1, null, 1), kauf(2, '  ', 2), kauf(3, 'Brot', 3)]

        expect:
        ShoppingSuggestionRanker.rank(kaeufe, [], 15) == ['Brot']
    }

    def "Limit #limit begrenzt die Vorschläge auf die besten"() {
        given:
        def kaeufe = [kauf(1, 'A', 3), kauf(2, 'B', 2), kauf(3, 'C', 1)]

        expect:
        ShoppingSuggestionRanker.rank(kaeufe, [], limit) == erwartet

        where:
        limit || erwartet
        2     || ['A', 'B']
        3     || ['A', 'B', 'C']
        0     || []
    }

    def "Ohne Käufe gibt es keine Vorschläge"() {
        expect:
        ShoppingSuggestionRanker.rank([], ['Milch'], 15) == []
    }

    def "Standardvorschläge: Reihenfolge bleibt, Artikel der Liste und Duplikate fallen weg, Leerzeichen werden entfernt, Limit greift"() {
        given:
        def standard = ['Milch', ' Tomaten', 'Äpfel', 'milch', '', 'Joghurt', 'Beeren']

        expect:
        ShoppingSuggestionRanker.filterDefaults(standard, ['ÄPFEL', null], 3) == ['Milch', 'Tomaten', 'Joghurt']
    }

    /** Kauf mit fester ID; {@code tag} = Tage nach REFERENCE_DATE. */
    private static ShoppingPurchase kauf(long id, String name, int tag) {
        TestData.shoppingPurchase(id: id, itemName: name, purchasedAt: DateUtils.toDate(REFERENCE_DATE.plusDays(tag)))
    }
}
