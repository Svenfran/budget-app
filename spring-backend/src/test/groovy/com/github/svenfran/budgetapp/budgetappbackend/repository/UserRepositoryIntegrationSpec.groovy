package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException

class UserRepositoryIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(UserRepositoryIntegrationSpec)

    @Autowired UserRepository userRepository

    // Das Repository vergleicht exakt. Groß-/Kleinschreibung und Leerzeichen spielen trotzdem keine Rolle (Konzept Q2,
    // DIV-22): E-Mails werden beim Speichern normalisiert (User.setEmail) und vor jedem Lookup ebenso
    // (DataLoaderService, VerificationService) – siehe DataLoaderServiceIntegrationSpec.
    def "Suche per E-Mail '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfredId = fixtures.user(email: 'alfred@divvy.test').id
        LOG.info('Szenario: User {} mit alfred@divvy.test', alfredId)

        when:
        def foundId = userRepository.findByEmail(gesucht).map { it.id }.orElse(null)
        LOG.info('userRepository.findByEmail(\'{}\') -> User {}', gesucht, foundId)

        then:
        foundId == (gefunden ? alfredId : null)

        where:
        gesucht                || gefunden
        'alfred@divvy.test'    || true
        'unbekannt@divvy.test' || false
    }

    def "E-Mail wird normalisiert gespeichert"() {
        given:
        LOG.info('Szenario: neuer Nutzer mit \'  Alfred@Divvy.TEST \'')

        when:
        def alfredId = fixtures.user(email: '  Alfred@Divvy.TEST ').id
        flushAndClear()
        def email = userRepository.findById(alfredId).get().email
        LOG.info('fixtures.user(email: \'  Alfred@Divvy.TEST \') -> User {} gespeichert mit {}', alfredId, email)

        then:
        email == 'alfred@divvy.test'
        def idByEmail = userRepository.findByEmail('alfred@divvy.test').get().id
        idByEmail == alfredId
    }

    def "Unique-Constraint verhindert zwei Nutzer mit derselben E-Mail in anderer Schreibweise"() {
        given:
        def alfred = fixtures.user(email: 'alfred@divvy.test')
        LOG.info('Szenario: User {} mit alfred@divvy.test', alfred.id)

        when:
        LOG.info('fixtures.user(email: \'ALFRED@divvy.test\'), erwartet DataIntegrityViolationException')
        fixtures.user(email: 'ALFRED@divvy.test')

        then: 'Ursache und Constraint-Name so, wie RestResponseEntityExceptionHandler sie für die 400 erwartet'
        def e = thrown(DataIntegrityViolationException)
        e.cause instanceof ConstraintViolationException
        (e.cause as ConstraintViolationException).constraintName == 'uk_user_details_email'
    }

    def "Suche per Name '#gesucht' findet den Nutzer: #gefunden"() {
        given:
        def alfredId = fixtures.user(name: 'alfred').id
        LOG.info('Szenario: User {} mit Name alfred', alfredId)

        when:
        def foundId = userRepository.findByName(gesucht).map { it.id }.orElse(null)
        LOG.info('userRepository.findByName(\'{}\') -> User {}', gesucht, foundId)

        then:
        foundId == (gefunden ? alfredId : null)

        where:
        gesucht     || gefunden
        'alfred'    || true
        'unbekannt' || false
    }
}
