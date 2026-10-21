package com.github.svenfran.budgetapp.budgetappbackend.repository

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

class TokenRepositoryIntegrationSpec extends IntegrationSpec {

    @Autowired TokenRepository tokenRepository

    def "Token wird über seinen Wert gefunden, ein unbekannter Wert liefert nichts (gefunden: #gefunden)"() {
        given:
        def token = fixtures.token(fixtures.user(), token: 'token-bekannt')

        when:
        def result = tokenRepository.findByToken(gesucht)

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

        expect:
        tokenRepository.findAllValidTokensByUserAndDeviceId(user.id, 'device-1')*.id == (gueltig ? [token.id] : [])
        tokenRepository.findAllInvalidTokensByUserAndDeviceId(user.id, 'device-1')*.id == (ungueltig ? [token.id] : [])

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

        expect:
        tokenRepository.findAllValidTokensByUserAndDeviceId(anna.id, 'device-1').isEmpty()
        tokenRepository.findAllInvalidTokensByUserAndDeviceId(anna.id, 'device-1').isEmpty()
    }

    def "Alle Tokens eines Nutzers werden geladen, die anderer Nutzer nicht"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        def annaTokens = [fixtures.token(anna), fixtures.token(anna, deviceId: 'device-2')]
        fixtures.token(ben)

        expect:
        tokenRepository.findAllByUserId(anna.id)*.id.toSet() == annaTokens*.id.toSet()
    }
}
