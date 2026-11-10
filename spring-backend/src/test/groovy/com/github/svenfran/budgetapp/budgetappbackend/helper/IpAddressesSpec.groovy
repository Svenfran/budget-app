package com.github.svenfran.budgetapp.budgetappbackend.helper

import spock.lang.Specification

/** IP-Literale parsen und kanonisieren (DIV-64): nur echte Literale, nie Hostnamen, Listen oder Zone-IDs. */
class IpAddressesSpec extends Specification {

    def "Gültiges Literal '#eingabe' wird zu '#kanonisch'"() {
        expect:
        IpAddresses.canonicalize(eingabe) == Optional.of(kanonisch)

        where:
        eingabe                | kanonisch
        '203.0.113.7'          | '203.0.113.7'
        ' 203.0.113.7 '        | '203.0.113.7'
        '0.0.0.0'              | '0.0.0.0'
        '255.255.255.255'      | '255.255.255.255'
        '::1'                  | '0:0:0:0:0:0:0:1'
        '2001:DB8::1'          | '2001:db8:0:0:0:0:0:1'
        '2001:db8:0:0:0:0:0:1' | '2001:db8:0:0:0:0:0:1'
        '::ffff:203.0.113.7'   | '203.0.113.7'
    }

    def "Ungültiger Wert '#eingabe' ist kein IP-Literal"() {
        expect:
        IpAddresses.canonicalize(eingabe) == Optional.empty()

        where:
        eingabe << ['999.999.999.999', '1.2.3', '1.2.3.4.5', '01.2.3.4', 'localhost', 'cafe', 'example.org',
                    'fe80::1%eth0', '', '   ', null, '203.0.113.7, 198.51.100.1', '203.0.113.7,198.51.100.1',
                    '2001:db8::1, ::1', '2001:db8:::1', '[::1]']
    }
}
