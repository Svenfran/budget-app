package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope
import spock.lang.Specification

import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.ACCOUNT_IP
import static com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope.IP

/**
 * Schlüssel und Pseudonyme des Login-Rate-Limits (DIV-64): HMAC über normalisierte Werte, Scope trennt die Schlüssel,
 * kein Klartext, IPv6 auf /64 gekürzt.
 */
class LoginAttemptKeysSpec extends Specification {

    /** Dummy-Secrets, nur für Tests. */
    static final String SECRET = 'bG9naW4tYXR0ZW1wdC1rZXlzLXNwZWMtZHVtbXktc2VjcmV0'
    static final String ANDERES_SECRET = 'YW5kZXJlcy1kdW1teS1zZWNyZXQtbnVyLWZ1ZXItdGVzdHM='
    static final String EMAIL = 'max@example.de'
    static final String IPV4 = '203.0.113.7'

    def keys = new LoginAttemptKeys(SECRET)

    def "Schreibweise der E-Mail (#eingabe) ergibt denselben Schlüssel in jedem Scope"() {
        expect:
        LoginAttemptScope.values().every { keys.keyHash(it, eingabe, IPV4) == keys.keyHash(it, EMAIL, IPV4) }
        keys.accountPseudonym(eingabe) == keys.accountPseudonym(EMAIL)

        where:
        eingabe << ['MAX@Example.de', ' max@example.de ', 'Max@EXAMPLE.DE\t']
    }

    def "Scope trennt die Schlüssel"() {
        when:
        def hashes = LoginAttemptScope.values().collect { keys.keyHash(it, EMAIL, IPV4) }

        then:
        hashes.toSet().size() == 3
    }

    def "Anderes Secret ergibt andere Schlüssel und Pseudonyme"() {
        given:
        def andere = new LoginAttemptKeys(ANDERES_SECRET)

        expect:
        LoginAttemptScope.values().every { andere.keyHash(it, EMAIL, IPV4) != keys.keyHash(it, EMAIL, IPV4) }
        andere.accountPseudonym(EMAIL) != keys.accountPseudonym(EMAIL)
        andere.ipPseudonym(IPV4) != keys.ipPseudonym(IPV4)
    }

    def "Schlüssel sind 64 Hex-Zeichen, Pseudonyme 16 Hex-Zeichen und Präfix des Schlüssels"() {
        when:
        def hashes = LoginAttemptScope.values().collect { keys.keyHash(it, EMAIL, IPV4) }
        def account = keys.accountPseudonym(EMAIL)
        def ip = keys.ipPseudonym(IPV4)

        then:
        hashes.every { it ==~ /[0-9a-f]{64}/ }
        account ==~ /[0-9a-f]{16}/
        ip ==~ /[0-9a-f]{16}/
        keys.keyHash(ACCOUNT, EMAIL, null).startsWith(account)
        keys.keyHash(IP, null, IPV4).startsWith(ip)
    }

    def "Schlüssel und Pseudonyme enthalten weder E-Mail noch IP im Klartext"() {
        when:
        def werte = LoginAttemptScope.values().collect { keys.keyHash(it, EMAIL, IPV4) } +
                [keys.accountPseudonym(EMAIL), keys.ipPseudonym(IPV4)]

        then:
        werte.every { !it.contains(EMAIL) && !it.contains(IPV4) && !it.contains('@') && !it.contains('.') }
    }

    def "IPv4 bleibt unverändert"() {
        expect:
        keys.ipValue(IPV4) == IPV4
    }

    def "Zwei IPv6-Adressen desselben /64 ergeben denselben IP- und Account+IP-Schlüssel, ein anderes /64 nicht"() {
        given:
        def a = '2001:db8:1:2:aaaa:bbbb:cccc:dddd'
        def b = '2001:db8:1:2::1'
        def anderesNetz = '2001:db8:1:3::1'

        expect:
        keys.ipValue(a) == '2001:db8:1:2:0:0:0:0/64'
        keys.ipValue(b) == keys.ipValue(a)
        keys.keyHash(IP, null, a) == keys.keyHash(IP, null, b)
        keys.keyHash(ACCOUNT_IP, EMAIL, a) == keys.keyHash(ACCOUNT_IP, EMAIL, b)
        keys.ipPseudonym(a) == keys.ipPseudonym(b)
        keys.keyHash(IP, null, anderesNetz) != keys.keyHash(IP, null, a)
    }

    def "IPv4-gemappte IPv6-Adresse zählt als IPv4"() {
        expect:
        keys.ipValue('::ffff:203.0.113.7') == IPV4
    }

    def "Leeres Secret wird beim Start abgelehnt"() {
        when:
        new LoginAttemptKeys(' ')

        then:
        thrown(IllegalStateException)
    }
}
