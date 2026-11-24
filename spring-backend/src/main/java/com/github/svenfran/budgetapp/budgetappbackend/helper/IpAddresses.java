package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parsen und Kanonisieren von IP-Literalen (DIV-64). Akzeptiert nur echte Literale – nie Hostnamen (kein DNS-Lookup),
 * keine Listen, keine IPv6-Zone-IDs.
 */
public final class IpAddresses {

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile(OCTET + "(\\." + OCTET + "){3}");
    /** Nur Hex-Ziffern, ':' und '.' (eingebettetes IPv4); schließt Hostnamen, Zone-IDs und Listen aus. */
    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9a-fA-F:.]+");

    private IpAddresses() {
    }

    /**
     * Kanonische Form eines IP-Literals ({@link InetAddress#getHostAddress()}): IPv4 unverändert, IPv6 klein und
     * ausgeschrieben (z. B. {@code 2001:db8:0:0:0:0:0:1}), IPv4-gemappte IPv6-Adressen als IPv4. Leer, wenn der Wert
     * kein gültiges Literal ist.
     */
    public static Optional<String> canonicalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        var candidate = value.trim();
        boolean ipv4 = IPV4.matcher(candidate).matches();
        boolean ipv6 = !ipv4 && candidate.indexOf(':') >= 0 && IPV6_CHARS.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return Optional.empty();
        }
        try {
            // Literal (durch die Vorprüfung sichergestellt) -> InetAddress parst nur, kein DNS-Lookup
            return Optional.of(InetAddress.getByName(candidate).getHostAddress());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    }
}
