package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

class UserRepositoryIntegrationSpec extends IntegrationSpec {

    @Autowired UserRepository userRepository

    // Groß-/Kleinschreibung: Ist-Verhalten ist ein case-sensitiver Vergleich (Postgres). Fachlich soll die Anmeldung
    // unabhängig davon funktionieren (Konzept Q2) – Umsetzung in DIV-22, dann die letzte Zeile anpassen.
    def "Suche per E-Mail '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfred = fixtures.user(email: 'alfred@divvy.test')

        when:
        def result = userRepository.findByEmail(gesucht)

        then:
        result.isPresent() == gefunden
        !gefunden || result.get().id == alfred.id

        where:
        gesucht                || gefunden
        'alfred@divvy.test'    || true
        'unbekannt@divvy.test' || false
        'Alfred@Divvy.test'    || false
    }

    def "Suche per Name '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfred = fixtures.user(name: 'alfred')

        when:
        def result = userRepository.findByName(gesucht)

        then:
        result.isPresent() == gefunden
        !gefunden || result.get().id == alfred.id

        where:
        gesucht     || gefunden
        'alfred'    || true
        'unbekannt' || false
    }
}
