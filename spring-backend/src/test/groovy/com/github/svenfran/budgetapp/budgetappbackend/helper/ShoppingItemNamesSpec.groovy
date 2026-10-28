package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

/**
 * Vergleichsschlüssel von Eintragsnamen (DIV-23): trim und Kleinschreibung, fehlende oder leere Namen sind mit nichts
 * vergleichbar.
 */
class ShoppingItemNamesSpec extends Specification {

    def "#fall: normalize(#name) ergibt #erwartet"() {
        expect:
        ShoppingItemNames.normalize(name) == erwartet

        where:
        fall                        | name          || erwartet
        'unverändert'               | 'milch'       || 'milch'
        'Groß-/Kleinschreibung'     | 'MiLCH'       || 'milch'
        'Leerzeichen außen'         | '  Milch '    || 'milch'
        'Leerzeichen innen bleiben' | 'Hafer Milch' || 'hafer milch'
        'Umlaut'                    | 'ÄPFEL'       || 'äpfel'
        'null'                      | null          || null
        'leer'                      | ''            || null
        'nur Leerzeichen'           | '   '         || null
    }
}
