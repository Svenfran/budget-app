package com.github.svenfran.budgetapp.budgetappbackend.config

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

import static com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp.Source.HEADER
import static com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp.Source.REMOTE_ADDR

/**
 * Quelle der Client-IP für das Login-Rate-Limit (DIV-64, AC7): ohne konfigurierten Header nur remoteAddr; mit Header
 * genau dieser eine Wert, sonst Fallback auf remoteAddr. X-Forwarded-For wird nie gelesen.
 */
class ClientIpResolverSpec extends Specification {

    static final String REMOTE = '198.51.100.99'

    def "Ohne konfigurierten Header gilt remoteAddr, Forwarding-Header werden ignoriert"() {
        given:
        def resolver = new ClientIpResolver('')
        def request = request('X-Real-IP': ['203.0.113.7'], 'X-Forwarded-For': ['203.0.113.8'])

        expect:
        resolver.resolve(request) == new ClientIp(REMOTE, REMOTE_ADDR)
    }

    def "Mit Header X-Real-IP gilt dessen Wert (kanonisiert)"() {
        given:
        def resolver = new ClientIpResolver('X-Real-IP')

        expect:
        resolver.resolve(request('X-Real-IP': [wert])) == new ClientIp(erwartet, HEADER)

        where:
        wert             | erwartet
        '203.0.113.7'    | '203.0.113.7'
        ' 203.0.113.7 '  | '203.0.113.7'
        '2001:DB8::1'    | '2001:db8:0:0:0:0:0:1'
    }

    def "Header-Name wird ohne Rücksicht auf Groß-/Kleinschreibung gelesen"() {
        given:
        def resolver = new ClientIpResolver('X-Real-IP')

        expect:
        resolver.resolve(request('x-real-ip': ['203.0.113.7'])) == new ClientIp('203.0.113.7', HEADER)
    }

    def "Unbrauchbarer Header (#fall) fällt auf remoteAddr zurück"() {
        given:
        def resolver = new ClientIpResolver('X-Real-IP')

        expect:
        resolver.resolve(request(headers)) == new ClientIp(REMOTE, REMOTE_ADDR)

        where:
        fall                          | headers
        'fehlt'                       | [:]
        'doppelt'                     | ['X-Real-IP': ['203.0.113.7', '203.0.113.8']]
        'Komma-Liste'                 | ['X-Real-IP': ['203.0.113.7, 203.0.113.8']]
        'kein IP-Literal'             | ['X-Real-IP': ['localhost']]
        'ungültige IPv4'              | ['X-Real-IP': ['999.1.1.1']]
        'leer'                        | ['X-Real-IP': ['']]
        'nur X-Forwarded-For gesetzt' | ['X-Forwarded-For': ['203.0.113.7']]
    }

    def "X-Forwarded-For wird auch neben einem gültigen X-Real-IP nicht gelesen"() {
        given:
        def resolver = new ClientIpResolver('X-Real-IP')
        def request = request('X-Real-IP': ['203.0.113.7'], 'X-Forwarded-For': ['192.0.2.1, 203.0.113.7'])

        expect:
        resolver.resolve(request) == new ClientIp('203.0.113.7', HEADER)
    }

    def "Ungültiger Header-Name (#name) verhindert den Start"() {
        when:
        new ClientIpResolver(name)

        then:
        thrown(IllegalStateException)

        where:
        name << ['X-Real-IP,X-Forwarded-For', 'X Real IP', 'X-Real-IP;']
    }

    private static MockHttpServletRequest request(Map<String, List<String>> headers) {
        def request = new MockHttpServletRequest('POST', '/api/auth/authenticate')
        request.remoteAddr = REMOTE
        headers.each { name, values -> values.each { request.addHeader(name, it) } }
        request
    }
}
