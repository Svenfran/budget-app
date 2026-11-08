package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

import static java.time.LocalDate.of

/**
 * CSV-Export als ZIP (DIV-68): ZIP in einen Puffer schreiben und zurücklesen. Jede Datei UTF-8 mit BOM, Trenner ;,
 * Zeilenende CRLF, Dateinamen und Kopfzeilen in der Sprache des Translators.
 */
class CsvZipWriterSpec extends Specification {

    private static final String BOM = '﻿'

    def "Einzelgruppe: ausgaben.csv und mitgliederhistorie.csv im Root mit BOM, CRLF und Zeilen"() {
        given:
        def gruppe = TestData.groupExport(groupName: 'Haushalt',
                carts: [TestData.cartExportRow(userName: 'Anna', title: 'Pizza', description: 'Freitag',
                        datePurchased: of(2025, 3, 7), amount: 12.5d, category: 'Essen', groupName: 'Haushalt')],
                memberships: [TestData.membershipExportRow(userName: 'Anna', start: of(2024, 1, 1), type: 'OWNER'),
                              TestData.membershipExportRow(userName: 'Ben', start: of(2024, 2, 1), end: of(2024, 6, 30))])

        when:
        def dateien = schreiben([gruppe], false)

        then:
        dateien.keySet().toList() == ['ausgaben.csv', 'mitgliederhistorie.csv']
        dateien['ausgaben.csv'] == BOM +
                'Benutzername;Titel;Beschreibung;Datum;Betrag;Kategorie;Gruppe\r\n' +
                'Anna;Pizza;Freitag;2025-03-07;12.50;Essen;Haushalt\r\n'
        dateien['mitgliederhistorie.csv'] == BOM +
                'Benutzername;Startdatum;Enddatum;Status\r\n' +
                'Anna;2024-01-01;;OWNER\r\n' +
                'Ben;2024-02-01;2024-06-30;MEMBER\r\n'
    }

    def "Sonderzeichen, Formeln, negative und große Beträge, fehlende Beschreibung"() {
        given:
        def gruppe = TestData.groupExport(carts: [
                TestData.cartExportRow(userName: 'Zoë', title: 'Brot; "Bio"', description: null, amount: -20d,
                        datePurchased: of(2025, 5, 1), category: '=HYPERLINK("x")', groupName: 'WG 🏠'),
                TestData.cartExportRow(title: 'Auto', description: 'Zeile1\nZeile2', amount: 1234567.89d,
                        datePurchased: of(2025, 4, 1), userName: 'Anna', category: 'Mobilität', groupName: 'WG 🏠')])

        when:
        def zeilen = schreiben([gruppe], false)['ausgaben.csv'].split('\r\n', -1).toList()

        then:
        zeilen[1] == 'Zoë;"Brot; ""Bio""";;2025-05-01;-20.00;"\'=HYPERLINK(""x"")";WG 🏠'
        zeilen[2] == 'Anna;Auto;"Zeile1\nZeile2";2025-04-01;1234567.89;Mobilität;WG 🏠'
        zeilen[3] == ''
        zeilen.size() == 4
    }

    def "Alle Gruppen: ein Ordner je Gruppe ohne ID, gleichnamige Gruppen mit Zähler"() {
        given:
        def erste = TestData.groupExport(groupId: 4711L, groupName: 'Haushalt', carts: [TestData.cartExportRow(title: 'Brot')])
        def zweite = TestData.groupExport(groupId: 4712L, groupName: 'Haushalt', carts: [TestData.cartExportRow(title: 'Kino')])

        when:
        def dateien = schreiben([erste, zweite], true)

        then:
        dateien.keySet().toList() == ['Haushalt/ausgaben.csv', 'Haushalt/mitgliederhistorie.csv',
                                      'Haushalt (2)/ausgaben.csv', 'Haushalt (2)/mitgliederhistorie.csv']
        dateien['Haushalt/ausgaben.csv'].contains(';Brot;')
        !dateien['Haushalt/ausgaben.csv'].contains(';Kino;')
        dateien['Haushalt (2)/ausgaben.csv'].contains(';Kino;')
        dateien['Haushalt (2)/mitgliederhistorie.csv'] == BOM + 'Benutzername;Startdatum;Enddatum;Status\r\n'
    }

    def "Ohne Gruppen (#umfang): beide Dateien nur mit Kopfzeile im Root"() {
        when:
        def dateien = schreiben([], allGroups)

        then:
        dateien == ['ausgaben.csv'          : BOM + 'Benutzername;Titel;Beschreibung;Datum;Betrag;Kategorie;Gruppe\r\n',
                    'mitgliederhistorie.csv': BOM + 'Benutzername;Startdatum;Enddatum;Status\r\n']

        where:
        umfang         | allGroups
        'Einzelgruppe' | false
        'Alle Gruppen' | true
    }

    def "Sprache #locale: Dateinamen #dateinamen und lokalisierte Kopfzeile"() {
        when:
        def dateien = schreiben([TestData.groupExport()], false, locale)

        then:
        dateien.keySet().toList() == dateinamen
        dateien[dateinamen[0]].startsWith(BOM + kopf)

        where:
        locale                     || dateinamen                                       | kopf
        Locale.GERMAN              || ['ausgaben.csv', 'mitgliederhistorie.csv']       | 'Benutzername;Titel;'
        Locale.ENGLISH             || ['expenses.csv', 'membership-history.csv']       | 'Username;Title;'
        Locale.forLanguageTag('es')|| ['gastos.csv', 'historial-miembros.csv']         | 'Nombre de usuario;Título;'
    }

    def "Einzelgruppen-Export mit mehreren Gruppen wird abgelehnt"() {
        when:
        new CsvZipWriter([TestData.groupExport(), TestData.groupExport()], false, TestData.translator())

        then:
        thrown(IllegalArgumentException)
    }

    /** Dateiname → Inhalt (UTF-8 inkl. BOM), in der Reihenfolge der ZIP-Einträge. */
    private static Map<String, String> schreiben(List<GroupExport> groups, boolean allGroups, Locale locale = Locale.GERMAN) {
        def buffer = new ByteArrayOutputStream()
        new CsvZipWriter(groups, allGroups, TestData.translator(locale)).write(buffer)
        def result = [:] as LinkedHashMap<String, String>
        new ZipInputStream(new ByteArrayInputStream(buffer.toByteArray()), StandardCharsets.UTF_8).withCloseable { zip ->
            for (def entry = zip.nextEntry; entry != null; entry = zip.nextEntry) {
                result[entry.name] = new String(zip.readAllBytes(), StandardCharsets.UTF_8)
            }
        }
        result
    }
}
