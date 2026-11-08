package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.helper.export.GroupExport
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import spock.lang.Specification

import static java.time.LocalDate.of

/**
 * XLSX-Export (DIV-68): Arbeitsmappe in einen Puffer schreiben und mit POI zurücklesen. Das Einzelgruppen-Layout ist
 * das bisherige (Blätter „Ausgaben“/„Mitgliedszeitraum“, Datumsformat dd.MM.yyyy, leeres Enddatum).
 */
class ExcelWriterSpec extends Specification {

    def "Einzelgruppe: genau zwei Blätter mit bisherigen Namen, Kopfzeilen und Werten"() {
        given:
        def gruppe = TestData.groupExport(groupName: 'Haushalt',
                carts: [TestData.cartExportRow(userName: 'Anna', title: 'Pizza', description: 'Freitag',
                        datePurchased: of(2025, 3, 7), amount: 12.345d, category: 'Essen', groupName: 'Haushalt')],
                memberships: [TestData.membershipExportRow(userName: 'Anna', start: of(2024, 1, 1), type: 'OWNER'),
                              TestData.membershipExportRow(userName: 'Ben', start: of(2024, 2, 1), end: of(2024, 6, 30))])

        when:
        def workbook = schreiben([gruppe], false)
        def ausgaben = workbook.getSheet('Ausgaben')
        def historie = workbook.getSheet('Mitgliedszeitraum')

        then:
        blattNamen(workbook) == ['Ausgaben', 'Mitgliedszeitraum']
        zeile(ausgaben, 0) == ['Benutzername', 'Titel', 'Beschreibung', 'Datum', 'Betrag', 'Kategorie', 'Gruppe']
        zeile(historie, 0) == ['Benutzername', 'Startdatum', 'Enddatum', 'Status']

        and: "Ausgabe: Datum als Datumszelle, Betrag numerisch ungerundet"
        ausgaben.lastRowNum == 1
        def cart = ausgaben.getRow(1)
        [0, 1, 2, 5, 6].collect { cart.getCell(it).stringCellValue } == ['Anna', 'Pizza', 'Freitag', 'Essen', 'Haushalt']
        cart.getCell(3).localDateTimeCellValue.toLocalDate() == of(2025, 3, 7)
        cart.getCell(3).cellStyle.dataFormatString == 'dd.MM.yyyy'
        cart.getCell(4).cellType == CellType.NUMERIC
        cart.getCell(4).numericCellValue == 12.345d

        and: "Historie: offene Mitgliedschaft mit leerem Enddatum"
        historie.lastRowNum == 2
        historie.getRow(1).getCell(0).stringCellValue == 'Anna'
        historie.getRow(1).getCell(1).localDateTimeCellValue.toLocalDate() == of(2024, 1, 1)
        historie.getRow(1).getCell(2).cellType == CellType.BLANK
        historie.getRow(1).getCell(3).stringCellValue == 'OWNER'
        historie.getRow(2).getCell(2).localDateTimeCellValue.toLocalDate() == of(2024, 6, 30)
        historie.getRow(2).getCell(3).stringCellValue == 'MEMBER'
    }

    def "Alle Gruppen: zwei eindeutig benannte Blätter je Gruppe mit den Daten der jeweiligen Gruppe"() {
        given:
        def erste = TestData.groupExport(groupName: 'Haushalt', carts: [TestData.cartExportRow(title: 'Brot')])
        def zweite = TestData.groupExport(groupName: 'Haushalt',
                carts: [TestData.cartExportRow(title: 'Kino'), TestData.cartExportRow(title: 'Bar')],
                memberships: [TestData.membershipExportRow(userName: 'Cleo')])
        def dritte = TestData.groupExport(groupName: 'Urlaub')

        when:
        def workbook = schreiben([erste, zweite, dritte], true)

        then:
        blattNamen(workbook) == ['Haushalt Ausgaben', 'Haushalt Mitglieder', 'Haushalt (2) Ausgaben',
                                 'Haushalt (2) Mitglieder', 'Urlaub Ausgaben', 'Urlaub Mitglieder']
        titel(workbook.getSheet('Haushalt Ausgaben')) == ['Brot']
        titel(workbook.getSheet('Haushalt (2) Ausgaben')) == ['Kino', 'Bar']
        workbook.getSheet('Haushalt (2) Mitglieder').getRow(1).getCell(0).stringCellValue == 'Cleo'

        and: "Gruppe ohne Carts und ohne Historie: Blätter nur mit Kopfzeile"
        workbook.getSheet('Urlaub Ausgaben').lastRowNum == 0
        workbook.getSheet('Urlaub Mitglieder').lastRowNum == 0
        zeile(workbook.getSheet('Urlaub Ausgaben'), 0).size() == 7
    }

    def "Ohne Gruppen (#umfang): gültige Datei mit zwei Kopfzeilen-Blättern"() {
        when:
        def workbook = schreiben([], allGroups)

        then:
        blattNamen(workbook) == ['Ausgaben', 'Mitgliedszeitraum']
        workbook.collect { it.lastRowNum } == [0, 0]
        zeile(workbook.getSheet('Mitgliedszeitraum'), 0) == ['Benutzername', 'Startdatum', 'Enddatum', 'Status']

        where:
        umfang         | allGroups
        'Einzelgruppe' | false
        'Alle Gruppen' | true
    }

    def "Englisch: Blattnamen und Kopfzeilen aus dem englischen Bundle"() {
        when:
        def einzeln = schreiben([TestData.groupExport()], false, Locale.ENGLISH)
        def alle = schreiben([TestData.groupExport(groupName: 'Home')], true, Locale.ENGLISH)

        then:
        blattNamen(einzeln) == ['Expenses', 'Membership period']
        zeile(einzeln.getSheet('Expenses'), 0) == ['Username', 'Title', 'Description', 'Date', 'Amount', 'Category', 'Group']
        blattNamen(alle) == ['Home Expenses', 'Home Members']
    }

    def "Einzelgruppen-Export mit mehreren Gruppen wird abgelehnt"() {
        when:
        new ExcelWriter([TestData.groupExport(), TestData.groupExport()], false, TestData.translator())

        then:
        thrown(IllegalArgumentException)
    }

    private static XSSFWorkbook schreiben(List<GroupExport> groups, boolean allGroups, Locale locale = Locale.GERMAN) {
        def buffer = new ByteArrayOutputStream()
        new ExcelWriter(groups, allGroups, TestData.translator(locale)).write(buffer)
        new XSSFWorkbook(new ByteArrayInputStream(buffer.toByteArray()))
    }

    private static List<String> blattNamen(XSSFWorkbook workbook) {
        workbook.collect { it.sheetName }
    }

    private static List<String> zeile(Sheet sheet, int index) {
        sheet.getRow(index).collect { it.stringCellValue }
    }

    private static List<String> titel(Sheet sheet) {
        (1..sheet.lastRowNum).collect { sheet.getRow(it).getCell(1).stringCellValue }
    }
}
