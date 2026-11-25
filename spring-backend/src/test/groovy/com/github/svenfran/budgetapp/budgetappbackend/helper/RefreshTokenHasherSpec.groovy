package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

/** Refresh-Token-Erzeugung und HMAC-Hash (DIV-65). Nur Dummy-Secrets. */
class RefreshTokenHasherSpec extends Specification {

    private static final String SECRET = 'dummy-secret-for-unit-tests'

    def hasher = new RefreshTokenHasher(SECRET)

    def "Neuer Token ist Base64url ohne Padding mit 43 Zeichen (32 Byte)"() {
        when:
        def token = hasher.newToken()

        then:
        token ==~ /[A-Za-z0-9_-]{43}/
        Base64.urlDecoder.decode(token).length == 32
    }

    def "Zwei neue Tokens sind verschieden"() {
        expect:
        hasher.newToken() != hasher.newToken()
    }

    def "Hash ist deterministisch, 64 Hex-Zeichen und ungleich dem Klartext"() {
        given:
        def token = hasher.newToken()

        when:
        def hash = hasher.hash(token)

        then:
        hash ==~ /[0-9a-f]{64}/
        hash == hasher.hash(token)
        hash != token
        hash != hasher.hash(hasher.newToken())
    }

    def "Anderes Secret (Pepper) ergibt einen anderen Hash"() {
        given:
        def token = hasher.newToken()

        expect:
        new RefreshTokenHasher('another-dummy-secret').hash(token) != hasher.hash(token)
    }

    def "Leeres Secret wird beim Start abgelehnt (#fall)"() {
        when:
        new RefreshTokenHasher(secret)

        then:
        thrown(IllegalStateException)

        where:
        fall    | secret
        'null'  | null
        'blank' | '  '
    }
}
