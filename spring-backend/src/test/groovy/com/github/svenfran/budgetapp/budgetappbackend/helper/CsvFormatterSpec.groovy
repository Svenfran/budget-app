package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

import java.time.LocalDate

/** CSV-Formatierung des Ausgabenexports (DIV-68): Quoting, Formula-Injection, Beträge, Datum. */
class CsvFormatterSpec extends Specification {

    def "Zeile mit Semikolon als Trenner und CRLF; Feld '#feld' wird zu #erwartet"() {
        expect:
        CsvFormatter.line(['a', feld, 'b']) == "a;${erwartet};b\r\n"

        where:
        feld                 || erwartet
        'Pizza'              || 'Pizza'
        'Brot; Butter'       || '"Brot; Butter"'
        'Sag "Hallo"'        || '"Sag ""Hallo"""'
        'Zeile1\nZeile2'     || '"Zeile1\nZeile2"'
        'Zeile1\r\nZeile2'   || '"Zeile1\r\nZeile2"'
        ' vorne'             || '" vorne"'
        'hinten '            || '"hinten "'
        'Müsli 🥣 ñ ß'       || 'Müsli 🥣 ñ ß'
        ''                   || ''
        null                 || ''
    }

    def "Text '#wert' wird zu '#erwartet' (Formula-Injection-Schutz nur am Anfang)"() {
        expect:
        CsvFormatter.text(wert) == erwartet

        where:
        wert         || erwartet
        '=SUMME(A1)' || "'=SUMME(A1)"
        '+49 123'    || "'+49 123"
        '-Rabatt'    || "'-Rabatt"
        '@user'      || "'@user"
        '\tTab'      || "'\tTab"
        '\rCR'       || "'\rCR"
        'Preis=10'   || 'Preis=10'
        'Pizza'      || 'Pizza'
        ''           || ''
        null         || ''
    }

    def "Text mit Formel-Präfix und Zeilenumbruch wird zusätzlich gequotet"() {
        expect:
        CsvFormatter.line([CsvFormatter.text('\rX')]) == "\"'\rX\"\r\n"
    }

    def "Betrag #betrag wird ungerundet als #erwartet geschrieben"() {
        expect:
        CsvFormatter.amount(betrag) == erwartet

        where:
        betrag      || erwartet
        1234567.89d || '1234567.89'
        12.5d       || '12.50'
        -12.5d      || '-12.50'
        12.345d     || '12.345'
        40.0d       || '40.00'
        1.0E7d      || '10000000.00'
        0d          || '0.00'
        0.001d      || '0.001'
    }

    def "Datum wird ISO geschrieben, null als leeres Feld"() {
        expect:
        CsvFormatter.date(LocalDate.of(2025, 3, 7)) == '2025-03-07'
        CsvFormatter.date(null) == ''
    }
}
