package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.hibernate.exception.ConstraintViolationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

class UserRepositoryIntegrationSpec extends IntegrationSpec {

    @Autowired UserRepository userRepository

    // Das Repository vergleicht exakt. Groß-/Kleinschreibung und Leerzeichen spielen trotzdem keine Rolle (Konzept Q2,
    // DIV-22): E-Mails werden beim Speichern normalisiert (User.setEmail) und vor jedem Lookup ebenso
    // (DataLoaderService, VerificationService) – siehe DataLoaderServiceIntegrationSpec.
    def "Suche per E-Mail '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfredId = fixtures.user(email: 'alfred@divvy.test').id

        when:
        def foundId = userRepository.findByEmail(gesucht).map { it.id }.orElse(null)

        then:
        foundId == (gefunden ? alfredId : null)

        where:
        gesucht                || gefunden
        'alfred@divvy.test'    || true
        'unbekannt@divvy.test' || false
    }

    def "E-Mail wird normalisiert gespeichert"() {
        when:
        def alfredId = fixtures.user(email: '  Alfred@Divvy.TEST ').id
        flushAndClear()

        then:
        def email = userRepository.findById(alfredId).get().email
        email == 'alfred@divvy.test'
        def idByEmail = userRepository.findByEmail('alfred@divvy.test').get().id
        idByEmail == alfredId
    }

    def "Unique-Constraint verhindert zwei Nutzer mit derselben E-Mail in anderer Schreibweise"() {
        given:
        fixtures.user(email: 'alfred@divvy.test')

        when:
        fixtures.user(email: 'ALFRED@divvy.test')

        then: 'Ursache und Constraint-Name so, wie RestResponseEntityExceptionHandler sie für die 400 erwartet'
        def e = thrown(DataIntegrityViolationException)
        e.cause instanceof ConstraintViolationException
        (e.cause as ConstraintViolationException).constraintName == 'uk_user_details_email'
    }

    def "Suche per Name '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfredId = fixtures.user(name: 'alfred').id

        when:
        def foundId = userRepository.findByName(gesucht).map { it.id }.orElse(null)

        then:
        foundId == (gefunden ? alfredId : null)

        where:
        gesucht     || gefunden
        'alfred'    || true
        'unbekannt' || false
    }
}
