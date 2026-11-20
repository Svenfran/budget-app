package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

class MessageBundlesSpec extends Specification {

    def "Bundle #bundle hat dieselben Schlüssel wie das deutsche Basis-Bundle"() {
        expect:
        keysOf(bundle).toSet() == keysOf('messages').toSet()

        where:
        bundle << ['messages_en', 'messages_es']
    }

    def "Bundle #bundle enthält keine doppelten Schlüssel"() {
        given:
        def keys = keysOf(bundle)

        expect:
        keys.findAll { keys.count(it) > 1 }.unique() == []

        where:
        bundle << ['messages', 'messages_en', 'messages_es']
    }

    def "Kopfzeile #key hat in allen Sprachen dieselbe Spaltenzahl (Export, DIV-77: Währung nach Betrag)"() {
        expect:
        ['messages', 'messages_en', 'messages_es'].collect { valueOf(it, key).split(/\|/).size() }.unique() == [spalten]

        where:
        key                          || spalten
        'download.cart.header'       || 8
        'download.membership.header' || 4
    }

    private static String valueOf(String bundle, String key) {
        def resource = MessageBundlesSpec.classLoader.getResource("${bundle}.properties")
        def zeile = resource.getText('UTF-8').readLines().find { it.trim().startsWith("${key}=") }
        assert zeile != null: "${key} fehlt in ${bundle}"
        zeile.split('=', 2)[1]
    }

    /**
     * Liest die Schlüssel zeilenweise aus der Datei: java.util.Properties würde doppelte Schlüssel beim Laden
     * stillschweigend überschreiben. Fortsetzungszeilen (\ am Zeilenende) kommen in den Bundles nicht vor.
     */
    private static List<String> keysOf(String bundle) {
        def resource = MessageBundlesSpec.classLoader.getResource("${bundle}.properties")
        assert resource != null: "${bundle}.properties nicht gefunden"
        resource.getText('UTF-8').readLines()
                .collect { it.trim() }
                .findAll { it && !it.startsWith('#') && !it.startsWith('!') }
                .collect { it.split(/[=:]/, 2)[0].trim() }
    }
}
