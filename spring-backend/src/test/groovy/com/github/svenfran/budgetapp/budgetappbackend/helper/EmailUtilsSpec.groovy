package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

class EmailUtilsSpec extends Specification {

    def "normalize('#eingabe') == '#erwartet'"() {
        expect:
        EmailUtils.normalize(eingabe) == erwartet

        where:
        eingabe                || erwartet
        'max@example.de'       || 'max@example.de'
        'Max@Example.DE'       || 'max@example.de'
        '  max@example.de \t'  || 'max@example.de'
        null                   || null
    }

    def "Normalisierung hängt nicht von der Default-Locale ab (türkisches I)"() {
        given:
        def vorher = Locale.default
        Locale.default = new Locale('tr', 'TR')

        expect:
        EmailUtils.normalize('ILKA@EXAMPLE.DE') == 'ilka@example.de'

        cleanup:
        Locale.default = vorher
    }
}
