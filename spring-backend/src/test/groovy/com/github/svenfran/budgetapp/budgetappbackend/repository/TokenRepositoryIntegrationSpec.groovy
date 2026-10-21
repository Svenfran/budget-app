package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

class TokenRepositoryIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRepositoryIntegrationSpec)

    @Autowired TokenRepository tokenRepository

    def "Token wird über seinen Wert gefunden, ein unbekannter Wert liefert nichts (gefunden: #gefunden)"() {
        given:
        def token = fixtures.token(fixtures.user(), token: 'token-bekannt')
        LOG.info('Szenario: Token {} gespeichert, gesucht wird {}', token.id,
                gesucht == token.token ? 'der gespeicherte Wert' : 'ein anderer Wert')

        when:
        def result = tokenRepository.findByToken(gesucht)
        LOG.info('tokenRepository.findByToken(<Wert>) -> Token {}', result.map { it.id }.orElse(null))

        then:
        result.isPresent() == gefunden
        !gefunden || result.get().id == token.id

        where:
        gesucht         || gefunden
        'token-bekannt' || true
        'token-fremd'   || false
    }

    /*
     * ODER-Semantik der Queries (Konzept Q1, entschieden in DIV-8 / P1): "gültig" = nicht beide Flags gesetzt,
     * "ungültig" = mindestens ein Flag gesetzt. Mischzustände gelten daher als beides. Das ist als Ist-Verhalten
     * akzeptiert, weil der Produktionscode beide Flags immer gemeinsam setzt (AuthenticationService, LogoutService);
     * die Zeilen mit nur einem gesetzten Flag sind dort nicht erreichbar.
     */
    def "Token mit expired=#expired, revoked=#revoked auf #geraet: gültig=#gueltig, ungültig=#ungueltig"() {
        given:
        def user = fixtures.user()
        def token = fixtures.token(user, expired: expired, revoked: revoked,
                deviceId: geraet == 'eigenem Gerät' ? 'device-1' : 'device-2')
        LOG.info('Szenario: User {}, Token {} expired={}, revoked={}, deviceId {}', user.id, token.id, expired, revoked,
                token.deviceId)

        when:
        def gueltigeIds = tokenRepository.findAllValidTokensByUserAndDeviceId(user.id, 'device-1')*.id
        def ungueltigeIds = tokenRepository.findAllInvalidTokensByUserAndDeviceId(user.id, 'device-1')*.id
        LOG.info('tokenRepository.findAll(Valid|Invalid)TokensByUserAndDeviceId(User {}, device-1) -> gültig {}, ungültig {}',
                user.id, gueltigeIds, ungueltigeIds)

        then:
        gueltigeIds == (gueltig ? [token.id] : [])
        ungueltigeIds == (ungueltig ? [token.id] : [])

        where:
        expired | revoked | geraet          || gueltig | ungueltig
        false   | false   | 'eigenem Gerät' || true    | false
        true    | false   | 'eigenem Gerät' || true    | true
        false   | true    | 'eigenem Gerät' || true    | true
        true    | true    | 'eigenem Gerät' || false   | true
        false   | false   | 'fremdem Gerät' || false   | false
    }

    def "Tokens anderer Nutzer auf demselben Gerät zählen weder als gültig noch als ungültig"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        fixtures.token(ben)
        fixtures.token(ben, expired: true, revoked: true)
        LOG.info('Szenario: Anna {} ohne Tokens, Ben {} mit gültigem und ungültigem Token auf device-1', anna.id, ben.id)

        when:
        def gueltige = tokenRepository.findAllValidTokensByUserAndDeviceId(anna.id, 'device-1')
        def ungueltige = tokenRepository.findAllInvalidTokensByUserAndDeviceId(anna.id, 'device-1')
        LOG.info('tokenRepository.findAll(Valid|Invalid)TokensByUserAndDeviceId(User {}, device-1) -> gültig {}, ungültig {}',
                anna.id, gueltige*.id, ungueltige*.id)

        then:
        gueltige.isEmpty()
        ungueltige.isEmpty()
    }

    def "Alle Tokens eines Nutzers werden geladen, die anderer Nutzer nicht"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        def annaTokens = [fixtures.token(anna), fixtures.token(anna, deviceId: 'device-2')]
        def benToken = fixtures.token(ben)
        LOG.info('Szenario: Anna {} mit Tokens {}, Ben {} mit Token {}', anna.id, annaTokens*.id, ben.id, benToken.id)

        when:
        def geladenIds = tokenRepository.findAllByUserId(anna.id)*.id
        LOG.info('tokenRepository.findAllByUserId(User {}) -> Tokens {}', anna.id, geladenIds)

        then:
        geladenIds.toSet() == annaTokens*.id.toSet()
    }
}
