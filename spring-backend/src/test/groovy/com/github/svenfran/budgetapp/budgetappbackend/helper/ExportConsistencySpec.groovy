package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import spock.lang.Specification

import static java.time.LocalDate.of

/**
 * AC6 (DIV-68): Dieselben Exportdaten ergeben in XLSX und CSV dieselben fachlichen Werte, und eine Gruppe trägt in
 * Blatt- und Ordnernamen denselben Zähler. Beide Writer werden echt geschrieben und zurückgelesen.
 *
 * Normalisierung: Datum ISO, Beträge als BigDecimal ohne nachlaufende Nullen, leere Zelle = leeres Feld, das
 * Formula-Präfix ' des CSV wird entfernt (im XLSX sind Textzellen nie Formeln).
 */
class ExportConsistencySpec extends Specification {

    private static final String BOM = '﻿'

    def "XLSX und CSV enthalten für alle Gruppen dieselben Werte (Sonderzeichen, große Beträge, Historie, leere Gruppe)"() {
        given:
        def haushalt = TestData.groupExport(groupName: 'Haushalt', carts: [
                TestData.cartExportRow(userName: 'Zoë 🥳', title: 'Brot; "Bio"', description: null, amount: 1234567.89d,
                        datePurchased: of(2025, 5, 1), category: '=SUMME(1)'),
                TestData.cartExportRow(title: '-Rabatt', description: 'Zeile1\nZeile2', amount: -20d, datePurchased: of(2025, 4, 1)),
                TestData.cartExportRow(title: 'Krumm', amount: 12.345d, datePurchased: of(2025, 3, 1), category: '@home')],
                memberships: [TestData.membershipExportRow(userName: 'Anna', type: 'OWNER'),
                              TestData.membershipExportRow(userName: 'Ben', start: of(2023, 1, 1), end: of(2024, 6, 30))])
        def leer = TestData.groupExport(groupName: 'Leer')
        def groups = [haushalt, leer]

        when:
        def workbook = xlsx(groups)
        def zip = csvZip(groups)

        then: "gleiche Anzahl Dateien wie Blattpaare"
        workbook.numberOfSheets == 4
        zip.size() == 4

        and:
        xlsxZeilen(workbook.getSheet('Haushalt Ausgaben')) == csvZeilen(zip['Haushalt/ausgaben.csv'])
        xlsxZeilen(workbook.getSheet('Haushalt Mitglieder')) == csvZeilen(zip['Haushalt/mitgliederhistorie.csv'])
        xlsxZeilen(workbook.getSheet('Leer Ausgaben')) == csvZeilen(zip['Leer/ausgaben.csv'])
        xlsxZeilen(workbook.getSheet('Leer Mitglieder')) == csvZeilen(zip['Leer/mitgliederhistorie.csv'])

        and: "Stichprobe: Werte stehen tatsächlich drin"
        def ausgaben = csvZeilen(zip['Haushalt/ausgaben.csv'])
        ausgaben.size() == 4
        ausgaben[1] == ['Zoë 🥳', 'Brot; "Bio"', '', '2025-05-01', '1234567.89', '=SUMME(1)', 'Haushalt']
        ausgaben[2][1] == '-Rabatt'
        ausgaben[2][2] == 'Zeile1\nZeile2'
        ausgaben[2][4] == '-20'
        csvZeilen(zip['Haushalt/mitgliederhistorie.csv'])[2] == ['Ben', '2023-01-01', '2024-06-30', 'MEMBER']
    }

    def "Betrag 12.345 ist in XLSX und CSV derselbe, ungerundete Wert"() {
        given:
        def gruppe = TestData.groupExport(carts: [TestData.cartExportRow(amount: 12.345d)])

        when:
        def zelle = xlsx([gruppe], false).getSheet('Ausgaben').getRow(1).getCell(4).numericCellValue
        def feld = rohzeilen(csvZip([gruppe], false)['ausgaben.csv'])[1][4]

        then:
        zelle == 12.345d
        feld == '12.345'
        new BigDecimal(feld) == BigDecimal.valueOf(zelle)
    }

    def "Kollision erst nach Bereinigung oder Kürzung: Blatt- und Ordnername einer Gruppe tragen denselben Zähler"() {
        given: "jede Gruppe mit eindeutigem Cart-Titel und Mitgliedsnamen, um sie in beiden Dateien wiederzufinden"
        def namen = ['A<B', 'A B', 'A:B', 'Haushalt', 'haushalt', 'Wohngemeinschaft Musterstraße 12', 'Wohngemeinschaft Musterweg 3']
        def groups = namen.withIndex().collect { name, i ->
            TestData.groupExport(groupName: name, carts: [TestData.cartExportRow(title: "Marker-$i")],
                    memberships: [TestData.membershipExportRow(userName: "Mitglied-$i")])
        }

        when:
        def workbook = xlsx(groups)
        def zip = csvZip(groups)
        def zuordnung = (0..<namen.size()).collect { i ->
            def ausgabenBlatt = workbook.find { it.sheetName.endsWith(' Ausgaben') && it.getRow(1).getCell(1).stringCellValue == "Marker-$i" }
            def mitgliederBlatt = workbook.find { it.sheetName.endsWith(' Mitglieder') && it.getRow(1).getCell(0).stringCellValue == "Mitglied-$i" }
            def ausgabenOrdner = zip.find { it.key.endsWith('/ausgaben.csv') && it.value.contains(";Marker-$i;") }.key
            def mitgliederOrdner = zip.find { it.key.endsWith('/mitgliederhistorie.csv') && it.value.contains("Mitglied-$i;") }.key
            [blatt      : zaehler(ausgabenBlatt.sheetName - ~/ Ausgaben$/),
             mitglieder : zaehler(mitgliederBlatt.sheetName - ~/ Mitglieder$/),
             ordner     : zaehler(ausgabenOrdner - ~/\/ausgaben\.csv$/),
             ordnerHist : zaehler(mitgliederOrdner - ~/\/mitgliederhistorie\.csv$/)]
        }

        then: "beide Blätter und beide Dateien einer Gruppe liegen unter demselben Zähler"
        zuordnung.every { it.blatt == it.mitglieder && it.blatt == it.ordner && it.ordner == it.ordnerHist }

        and: "Vorbedingung: die Namen kollidieren tatsächlich"
        zuordnung*.blatt == [1, 2, 3, 1, 2, 1, 2]
    }

    private static int zaehler(String name) {
        def treffer = name =~ /\((\d+)\)$/
        treffer.find() ? treffer.group(1) as int : 1
    }

    private static XSSFWorkbook xlsx(List<GroupExport> groups, boolean allGroups = true) {
        def buffer = new ByteArrayOutputStream()
        new ExcelWriter(groups, allGroups, TestData.translator()).write(buffer)
        TestData.workbook(buffer.toByteArray())
    }

    private static Map<String, String> csvZip(List<GroupExport> groups, boolean allGroups = true) {
        def buffer = new ByteArrayOutputStream()
        new CsvZipWriter(groups, allGroups, TestData.translator()).write(buffer)
        TestData.unzip(buffer.toByteArray())
    }

    /** Alle Zeilen inkl. Kopfzeile, normalisiert. */
    private static List<List<String>> xlsxZeilen(Sheet sheet) {
        (0..sheet.lastRowNum).collect { r ->
            def row = sheet.getRow(r)
            int spalten = sheet.getRow(0).lastCellNum
            (0..<spalten).collect { c -> zellwert(row.getCell(c)) }
        }
    }

    private static String zellwert(Cell cell) {
        if (cell == null || cell.cellType == CellType.BLANK) return ''
        if (cell.cellType == CellType.NUMERIC) {
            return DateUtil.isCellDateFormatted(cell)
                    ? cell.localDateTimeCellValue.toLocalDate().toString()
                    : betrag(BigDecimal.valueOf(cell.numericCellValue))
        }
        cell.stringCellValue
    }

    /** CSV normalisiert: Beträge (Spalte, die eine Zahl ist) ohne nachlaufende Nullen, Formula-Präfix entfernt. */
    private static List<List<String>> csvZeilen(String csv) {
        rohzeilen(csv).collect { zeile ->
            zeile.collect { feld ->
                if (feld ==~ /-?\d+\.\d+/) return betrag(new BigDecimal(feld))
                feld ==~ /(?s)'[=+\-@\t\r].*/ ? feld.substring(1) : feld
            }
        }
    }

    /** Minimaler RFC-4180-Leser für ; und CRLF (Anführungszeichen, verdoppelte "" und Umbrüche im Feld). */
    private static List<List<String>> rohzeilen(String csv) {
        assert csv.startsWith(BOM)
        def text = csv.substring(1)
        def zeilen = []
        def zeile = []
        def feld = new StringBuilder()
        boolean inQuotes = false
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i)
            if (inQuotes) {
                if (ch == '"' as char && i + 1 < text.length() && text.charAt(i + 1) == '"' as char) { feld << '"'; i++ }
                else if (ch == '"' as char) inQuotes = false
                else feld << ch
            } else if (ch == '"' as char) {
                inQuotes = true
            } else if (ch == ';' as char) {
                zeile << feld.toString(); feld.setLength(0)
            } else if (ch == '\r' as char && i + 1 < text.length() && text.charAt(i + 1) == '\n' as char) {
                zeile << feld.toString(); feld.setLength(0)
                zeilen << zeile; zeile = []
                i++
            } else {
                feld << ch
            }
        }
        assert feld.length() == 0 && zeile.isEmpty(): 'CSV endet nicht mit CRLF'
        zeilen
    }

    private static String betrag(BigDecimal value) {
        value.stripTrailingZeros().toPlainString()
    }
}
