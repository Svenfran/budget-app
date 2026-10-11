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
