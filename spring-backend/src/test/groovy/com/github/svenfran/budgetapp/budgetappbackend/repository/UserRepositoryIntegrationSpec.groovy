package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

class UserRepositoryIntegrationSpec extends IntegrationSpec {

    @Autowired UserRepository userRepository

    // Das Repository vergleicht exakt. Groß-/Kleinschreibung und Leerzeichen spielen trotzdem keine Rolle (Konzept Q2,
    // DIV-22): E-Mails werden beim Speichern normalisiert (User.setEmail) und vor jedem Lookup ebenso
    // (DataLoaderService, VerificationService) – siehe DataLoaderServiceIntegrationSpec.
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
    }

    def "E-Mail wird normalisiert gespeichert"() {
        when:
        def alfred = fixtures.user(email: '  Alfred@Divvy.TEST ')
        flushAndClear()

        then:
        userRepository.findById(alfred.id).get().email == 'alfred@divvy.test'
        userRepository.findByEmail('alfred@divvy.test').get().id == alfred.id
    }

    def "Unique-Constraint verhindert zwei Nutzer mit derselben E-Mail in anderer Schreibweise"() {
        given:
        fixtures.user(email: 'alfred@divvy.test')

        when:
        fixtures.user(email: 'ALFRED@divvy.test')

        then:
        thrown(DataIntegrityViolationException)
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
