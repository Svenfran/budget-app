package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.MapBindingResult

/**
 * Commit-Verhalten des Passwort-Resets (DIV-65): Scheitert der Mailversand, wird die Änderung samt Session-Widerruf
 * zurückgerollt – der Nutzer bleibt mit altem Passwort und aktiven Sessions angemeldet statt ausgesperrt. Brevo wirft
 * eine geprüfte Exception; ohne rollbackFor würde die Transaktion trotzdem committen.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; der Stand wird per JDBC nach dem Commit gelesen.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserProfileServiceCommitIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceCommitIntegrationSpec)

    private static final String EMAIL = 'upc-anna@example.org'

    @Autowired UserProfileService userProfileService
    @Autowired AuthSessionService authSessionService
    @Autowired PasswordEncoder passwordEncoder
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    def setup() {
        databaseCleaner.truncateAll()
    }

    def "Passwort-Reset mit #versand: Passwort geändert #geaendert, Sessions widerrufen #geaendert"() {
        given:
        def anna = fixtures.user(email: EMAIL, password: passwordEncoder.encode('alt'))
        def sessionId = authSessionService.createSession(anna, 'device-1').sessionId()
        def hashVorher = passwortHash(anna.id)
        LOG.info('Szenario: Nutzer {} mit Passwort alt und Session {}; Passwort-Reset, Mailversand {}', anna.id, sessionId, versand)

        when:
        LOG.info('userProfileService.passwordReset({}), Mail {}', EMAIL, versand)
        Exception fehler = null
        try {
            userProfileService.passwordReset(EMAIL, new MapBindingResult([email: EMAIL], 'dto'))
        } catch (Exception e) {
            fehler = e
        }
        def hashNachher = passwortHash(anna.id)
        def widerrufen = jdbcTemplate.queryForObject('select revoke_reason from auth_session where id = ?', String, sessionId)
        LOG.info('-> Exception {}, Passwort geändert {}, Session {} widerrufen mit {}', fehler?.class?.simpleName,
                hashNachher != hashVorher, sessionId, widerrufen)

        then:
        1 * emailSenderService.sendEmail(EMAIL, _, _) >> { if (mailScheitert) throw new Exception('Brevo nicht erreichbar') }
        (fehler != null) == mailScheitert
        (hashNachher != hashVorher) == geaendert
        widerrufen == (geaendert ? 'PASSWORD_RESET' : null)
        passwordEncoder.matches('alt', hashNachher) == !geaendert

        where:
        versand        | mailScheitert || geaendert
        'erfolgreich'  | false         || true
        'gescheitert'  | true          || false
    }

    private String passwortHash(Long userId) {
        jdbcTemplate.queryForObject('select password from user_details where id = ?', String, userId)
    }
}
