package com.github.svenfran.budgetapp.budgetappbackend.helper;

import com.github.svenfran.budgetapp.budgetappbackend.constants.LoginAttemptScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Schlüssel und Log-Pseudonyme des Login-Rate-Limits (DIV-64).
 *
 * <ul>
 *   <li>Zählerschlüssel = {@code HMAC-SHA256(pepper, "<scope>:<wert>")} als Hex (64 Zeichen); {@code scope} klein
 *       geschrieben ({@code ip}, {@code account}, {@code account_ip}). E-Mail und IP liegen so nie im Klartext in der
 *       Tabelle; ein einfacher Hash wäre für IPv4 (2³² Werte) durch Durchprobieren umkehrbar.</li>
 *   <li>Log-Pseudonym = die ersten 16 Zeichen des Schlüssels von {@code account} bzw. {@code ip} – damit sind
 *       Log-Zeilen und Tabellenzeilen korrelierbar, ohne Klartext zu loggen.</li>
 *   <li>Pepper = {@code HMAC-SHA256(secret_key, "divvy-login-pseudonym-v1")}: aus dem JWT-Secret abgeleitet
 *       (Domänentrennung zum Signieren), keine eigene Env-Variable. Eine Rotation des Secrets ändert alle Schlüssel –
 *       die Zähler beginnen dann neu.</li>
 * </ul>
 *
 * Werte: Account = {@link EmailUtils#normalize}, IP = kanonisches IP-Literal, IPv6 auf das /64-Präfix gekürzt
 * (ein Angreifer verfügt typischerweise über ein ganzes /64). Die Schlüssel hängen nicht davon ab, ob der Account
 * existiert.
 */
@Component
public class LoginAttemptKeys {

    private static final String HMAC = "HmacSHA256";
    private static final String PEPPER_LABEL = "divvy-login-pseudonym-v1";
    private static final int PSEUDONYM_LENGTH = 16;
    private static final int IPV6_PREFIX_BYTES = 8;

    private final byte[] pepper;

    public LoginAttemptKeys(@Value("${secret_key}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("secret_key must be set (used to derive the login rate limit keys)");
        }
        this.pepper = hmac(secretKey.getBytes(StandardCharsets.UTF_8), PEPPER_LABEL);
    }

    /** Zählerschlüssel (Hex, 64 Zeichen) des Scopes für E-Mail und kanonische Client-IP. */
    public String keyHash(LoginAttemptScope scope, String email, String canonicalIp) {
        return hash(scope, value(scope, email, canonicalIp));
    }

    /** Log-Pseudonym (16 Zeichen) der E-Mail; Präfix des {@code ACCOUNT}-Schlüssels. */
    public String accountPseudonym(String email) {
        return keyHash(LoginAttemptScope.ACCOUNT, email, null).substring(0, PSEUDONYM_LENGTH);
    }

    /** Log-Pseudonym (16 Zeichen) der Client-IP; Präfix des {@code IP}-Schlüssels. */
    public String ipPseudonym(String canonicalIp) {
        return keyHash(LoginAttemptScope.IP, null, canonicalIp).substring(0, PSEUDONYM_LENGTH);
    }

    /** Normalisierte Login-Kennung (trim + lowercase). */
    public String accountValue(String email) {
        return EmailUtils.normalize(email);
    }

    /** IP-Wert des Schlüssels: IPv4 unverändert, IPv6 als {@code <präfix>/64}. Erwartet ein kanonisches IP-Literal. */
    public String ipValue(String canonicalIp) {
        if (canonicalIp == null || canonicalIp.indexOf(':') < 0) {
            return canonicalIp;
        }
        try {
            // Literal mit ':' -> kein DNS-Lookup
            var address = InetAddress.getByName(canonicalIp);
            if (address instanceof Inet4Address) {
                return address.getHostAddress();
            }
            var bytes = address.getAddress();
            Arrays.fill(bytes, IPV6_PREFIX_BYTES, bytes.length, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Not an IP literal", e);
        }
    }

    private String value(LoginAttemptScope scope, String email, String canonicalIp) {
        return switch (scope) {
            case IP -> ipValue(canonicalIp);
            case ACCOUNT -> accountValue(email);
            // Die IP enthält kein '|', die Trennung ist also eindeutig
            case ACCOUNT_IP -> accountValue(email) + "|" + ipValue(canonicalIp);
        };
    }

    private String hash(LoginAttemptScope scope, String value) {
        return HexFormat.of().formatHex(hmac(pepper, scope.name().toLowerCase(Locale.ROOT) + ":" + value));
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            var mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
