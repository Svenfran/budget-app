package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.helper.ExportNames.GroupNames
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

/**
 * Blatt- und Ordnernamen für „Alle Gruppen“ (DIV-68): ohne technische ID, Zähler bei Namensgleichheit gilt für alle drei
 * Namen einer Gruppe, Excel- und Dateisystemgrenzen.
 */
class ExportNamesSpec extends Specification {

    private static final String INVALID_SHEET_CHARS = /[\\\/?*\[\]:]/

    def "Gruppe ohne Kollision: Name plus Label, Ordner nur der Name"() {
        expect:
        names('Haushalt') == [new GroupNames('Haushalt Ausgaben', 'Haushalt Mitglieder', 'Haushalt')]
    }

    def "Gleichnamige Gruppen bekommen einen gemeinsamen Zähler für Blätter und Ordner"() {
        expect:
        names('Haushalt', 'Haushalt', 'Haushalt') == [
                new GroupNames('Haushalt Ausgaben', 'Haushalt Mitglieder', 'Haushalt'),
                new GroupNames('Haushalt (2) Ausgaben', 'Haushalt (2) Mitglieder', 'Haushalt (2)'),
                new GroupNames('Haushalt (3) Ausgaben', 'Haushalt (3) Mitglieder', 'Haushalt (3)')]
    }

    def "Kollision erst nach Bereinigung oder Groß-/Kleinschreibung (#erster / #zweiter) führt zum Zähler"() {
        when:
        def result = names(erster, zweiter)

        then:
        result[1] == zweitesErgebnis

        where:
        erster     | zweiter    || zweitesErgebnis
        'Haushalt' | 'haushalt' || new GroupNames('haushalt (2) Ausgaben', 'haushalt (2) Mitglieder', 'haushalt (2)')
        'A:B'      | 'A B'      || new GroupNames('A B (2) Ausgaben', 'A B (2) Mitglieder', 'A B (2)')
        'A[B]'     | 'A B'      || new GroupNames('A B (2) Ausgaben', 'A B (2) Mitglieder', 'A B (2)')
    }

    def "Kollision nur im Ordnernamen (A<B / A B): Zähler gilt auch für die Blätter"() {
        when:
        def result = names('A<B', 'A B')

        then: "Blattnamen allein wären verschieden, < ist dort erlaubt"
        result[0] == new GroupNames('A<B Ausgaben', 'A<B Mitglieder', 'A B')
        result[1] == new GroupNames('A B (2) Ausgaben', 'A B (2) Mitglieder', 'A B (2)')
    }

    def "Kollision nur im Blattnamen (gleiche ersten 22 Zeichen): Zähler gilt auch für den Ordner"() {
        when:
        def result = names('Wohngemeinschaft Musterstraße 12', 'Wohngemeinschaft Musterweg 3')

        then:
        result[0] == new GroupNames('Wohngemeinschaft Muste Ausgaben', 'Wohngemeinschaft Mus Mitglieder',
                'Wohngemeinschaft Musterstraße 12')
        result[1] == new GroupNames('Wohngemeinschaft M (2) Ausgaben', 'Wohngemeinschaft (2) Mitglieder',
                'Wohngemeinschaft Musterweg 3 (2)')
        result*.cartSheet()*.length() == [31, 31]
    }

    def "Sehr langer Name: nur der Name wird gekürzt, Zähler und Label bleiben vollständig"() {
        given:
        def lang = 'Ein wirklich sehr langer Gruppenname für die gemeinsame Urlaubskasse 2025'

        when:
        def result = names(lang, lang, lang)

        then:
        result*.cartSheet().every { it.length() <= 31 && it.endsWith(' Ausgaben') }
        result*.membershipSheet().every { it.length() <= 31 && it.endsWith(' Mitglieder') }
        result*.folder().every { it.length() <= 40 }
        result[2].cartSheet() == 'Ein wirklich sehr (3) Ausgaben'
        result[2].folder() == 'Ein wirklich sehr langer Gruppenname (3)'
    }

    def "Leerer oder nur aus Sonderzeichen bestehender Name ('#name') ergibt den Fallback"() {
        expect:
        names(name, '') == [new GroupNames('Gruppe Ausgaben', 'Gruppe Mitglieder', 'Gruppe'),
                            new GroupNames('Gruppe (2) Ausgaben', 'Gruppe (2) Mitglieder', 'Gruppe (2)')]

        where:
        name << ['', '   ', '???', '[*]', null]
    }

    def "Apostroph am Rand des Blattnamens wird entfernt, im Inneren bleibt er"() {
        expect:
        names("'Bob's Bude'")[0].cartSheet() == "Bob's Bude Ausgaben"
    }

    def "Ordnernamen: verbotene Zeichen ersetzt, kein abschließender Punkt"() {
        expect:
        names('A/B\\C:D*E?F"G<H>I|J')[0].folder() == 'A B C D E F G H I J'
        names('Haus...')[0].folder() == 'Haus'
        names('Müller & Söhne')[0].folder() == 'Müller Söhne'
    }

    def "Spanische Labels passen in 31 Zeichen"() {
        when:
        def result = ExportNames.assign([TestData.groupExport(groupName: 'Piso compartido Calle Mayor 12')],
                'Gastos', 'Miembros', 'Grupo').values().toList()

        then:
        result[0] == new GroupNames('Piso compartido Calle Ma Gastos', 'Piso compartido Calle Miembros',
                'Piso compartido Calle Mayor 12')
    }

    def "Namen enthalten keine Gruppen-ID und sind gültige Blattnamen"() {
        given:
        def groups = ['Haushalt', 'Haushalt', 'Urlaub: Spanien [2025]', 'x' * 50].withIndex().collect { name, i ->
            TestData.groupExport(groupId: 4711L + i, groupName: name)
        }

        when:
        def result = ExportNames.assign(groups, 'Ausgaben', 'Mitglieder', 'Gruppe').values().toList()
        def sheets = result*.cartSheet() + result*.membershipSheet()

        then:
        result.every { !(it.cartSheet() + it.membershipSheet() + it.folder()).contains('471') }
        sheets.every { it.length() <= 31 && !(it =~ INVALID_SHEET_CHARS) && !it.startsWith("'") && !it.endsWith("'") }
        sheets*.toLowerCase().unique().size() == sheets.size()
    }

    private static List<GroupNames> names(String... groupNames) {
        def groups = groupNames.collect { TestData.groupExport(groupName: it) }
        ExportNames.assign(groups, 'Ausgaben', 'Mitglieder', 'Gruppe').values().toList()
    }
}
