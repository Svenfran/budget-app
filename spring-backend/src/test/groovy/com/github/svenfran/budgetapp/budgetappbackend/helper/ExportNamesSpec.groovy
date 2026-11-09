package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.helper.ExportNames.GroupNames
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.apache.poi.ss.util.WorkbookUtil
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
        // Regression (Codex-Review 2, F-001): POI vergleicht Blattnamen mit equalsIgnoreCase; toLowerCase(Locale.ROOT)
        // hielt „İ“ (U+0130) und „i“ für verschieden, POI nicht
        'İstanbul' | 'istanbul' || new GroupNames('istanbul (2) Ausgaben', 'istanbul (2) Mitglieder', 'istanbul (2)')
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

    // Regression (Codex-Review 3, F-001): 40 Punkte + „X“ übersteht die Bereinigung, nach Kürzung auf 40 Zeichen und
    // Entfernen der abschließenden Punkte war der Ordnername leer (ZIP-Einträge „/ausgaben.csv“)
    def "Ordnername, der erst durch die Kürzung leer wird, erhält den Fallback samt Zähler"() {
        given:
        def punkte = '.' * 40 + 'X'

        when:
        def allein = names(punkte)
        def mitGruppe = names('Gruppe', punkte)

        then:
        allein[0].folder() == 'Gruppe'
        mitGruppe[0] == new GroupNames('Gruppe Ausgaben', 'Gruppe Mitglieder', 'Gruppe')
        mitGruppe[1] == new GroupNames('.' * 18 + ' (2) Ausgaben', '.' * 16 + ' (2) Mitglieder', 'Gruppe (2)')
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

    // Regression (Codex-Review 4, F-001): „' 'X“ wurde zu „'X Ausgaben“, POI lehnt Blattnamen mit ' am Rand ab
    def "Apostrophe und Leerraum am Rand werden gemeinsam entfernt (#name.inspect())"() {
        expect:
        names(name)[0] == erwartet

        where:
        name    || erwartet
        "' 'X"  || new GroupNames('X Ausgaben', 'X Mitglieder', 'X')
        "X' '"  || new GroupNames('X Ausgaben', 'X Mitglieder', 'X')
        "' ' '" || new GroupNames('Gruppe Ausgaben', 'Gruppe Mitglieder', 'Gruppe')
    }

    // F-002 (Codex-Review 4): reservierte Windows-Gerätenamen als ZIP-Ordner bekommen ein Präfix
    def "Reservierter Windows-Name '#name' wird als Ordner zu '#ordner', die Blätter bleiben"() {
        when:
        def result = names(name)[0]

        then:
        result.folder() == ordner
        result.cartSheet() == "$name Ausgaben".toString().trim().replace('  ', ' ')

        where:
        name      || ordner
        'CON'     || '_CON'
        'con'     || '_con'
        'NUL.txt' || '_NUL.txt'
        'COM1'    || '_COM1'
        'LPT9'    || '_LPT9'
        'AUX '    || '_AUX'
        'CONSOLE' || 'CONSOLE'
        'COM10'   || 'COM10'
    }

    def "Zweiter reservierter Name bekommt den Zähler, das Präfix nur, wo der Name reserviert ist"() {
        expect:
        names('CON', 'con')*.folder() == ['_CON', 'con (2)']
    }

    // Gesamttest (Phase 10): jeder schwierige Name einzeln ergibt gültige, nichtleere Namen
    def "Schwieriger Gruppenname #anzeige ergibt gültige Blatt- und Ordnernamen"() {
        expect:
        pruefeGueltig(names(name))

        where:
        name << TestData.TRICKY_GROUP_NAMES
        anzeige = String.valueOf(name?.inspect())
    }

    def "Alle schwierigen Gruppennamen zusammen ergeben gültige, eindeutige Namen (#locale)"() {
        given:
        def translator = TestData.translator(locale)
        def groups = TestData.TRICKY_GROUP_NAMES.collect { TestData.groupExport(groupName: it) }

        when:
        def result = ExportNames.assign(groups, translator.translate('download.cart.sheetname'),
                translator.translate('download.membership.sheetname.short'),
                translator.translate('download.group.fallback')).values().toList()

        then:
        result.size() == groups.size()
        pruefeGueltig(result)

        where:
        locale << [Locale.GERMAN, Locale.ENGLISH, Locale.forLanguageTag('es')]
    }

    /**
     * Blattnamen bestehen POIs eigene Prüfung (Länge, verbotene Zeichen, ' am Rand); Ordner sind nicht leer, höchstens
     * 40 Zeichen, nur erlaubte Zeichen, ohne Leerraum/Punkt am Ende und nicht reserviert; beides eindeutig wie POI bzw.
     * Windows vergleichen (ohne Groß-/Kleinschreibung).
     */
    private static boolean pruefeGueltig(List<GroupNames> result) {
        def sheets = result*.cartSheet() + result*.membershipSheet()
        sheets.each { sheet ->
            WorkbookUtil.validateSheetName(sheet)
            assert sheet.length() <= ExportNames.MAX_SHEET_NAME
        }
        result*.folder().each { folder ->
            assert folder && folder.length() <= ExportNames.MAX_FOLDER_NAME
            assert folder ==~ /[\p{L}\p{Nd} ._()\-]+/
            assert !folder.startsWith(' ') && !folder.endsWith(' ') && !folder.endsWith('.')
            assert !(folder ==~ /(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])\s*(\..*)?/)
        }
        assert eindeutig(sheets)
        assert eindeutig(result*.folder())
        true
    }

    private static boolean eindeutig(List<String> namen) {
        def set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        set.addAll(namen)
        set.size() == namen.size()
    }

    private static List<GroupNames> names(String... groupNames) {
        def groups = groupNames.collect { TestData.groupExport(groupName: it) }
        ExportNames.assign(groups, 'Ausgaben', 'Mitglieder', 'Gruppe').values().toList()
    }
}
