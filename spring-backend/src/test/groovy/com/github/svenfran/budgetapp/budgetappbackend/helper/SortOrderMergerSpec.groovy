package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

/**
 * Tolerante Übernahme einer gewünschten Reihenfolge (DIV-1, E2): fremde/gelöschte IDs ignorieren, Duplikate beim
 * ersten Vorkommen zählen, fehlende gespeicherte IDs in ihrer bisherigen Reihenfolge anhängen.
 */
class SortOrderMergerSpec extends Specification {

    def "#fall: gewünscht #gewuenscht, gespeichert #gespeichert ergibt #erwartet"() {
        expect:
        SortOrderMerger.merge(gewuenscht, gespeichert) == erwartet

        where:
        fall                              | gewuenscht         | gespeichert || erwartet
        'exakt'                           | [3L, 1L, 2L]       | [1L, 2L, 3L] || [3L, 1L, 2L]
        'unverändert'                     | [1L, 2L, 3L]       | [1L, 2L, 3L] || [1L, 2L, 3L]
        'unbekannte IDs ignoriert'        | [9L, 3L, 1L, 2L]   | [1L, 2L, 3L] || [3L, 1L, 2L]
        'Duplikat zählt beim ersten'      | [2L, 1L, 2L, 3L]   | [1L, 2L, 3L] || [2L, 1L, 3L]
        'fehlende hinten in alter Folge'  | [3L]               | [1L, 2L, 3L] || [3L, 1L, 2L]
        'null-ID ignoriert'               | [null, 2L]         | [1L, 2L]     || [2L, 1L]
        'Request null'                    | null               | [1L, 2L]     || [1L, 2L]
        'Request leer'                    | []                 | [1L, 2L]     || [1L, 2L]
        'nichts gespeichert'              | [1L, 2L]           | []           || []
    }
}
