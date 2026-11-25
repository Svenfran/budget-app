package com.github.svenfran.budgetapp.budgetappbackend.helper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh Tokens (DIV-65): erzeugt opake Zufallstokens und deren Speicher-Hash.
 *
 * <ul>
 *   <li>Token = 32 Byte aus {@link SecureRandom}, Base64url ohne Padding (43 Zeichen). Der Klartext existiert nur in
 *       der Response an den Client.</li>
 *   <li>Hash = {@code HMAC-SHA256(pepper, token)} als Hex (64 Zeichen). Bei 256 Bit Entropie genügt ein schneller
 *       Hash (kein bcrypt); der Pepper verhindert, dass ein DB-Abzug allein zum Prüfen von Tokens reicht.</li>
 *   <li>Pepper = {@code HMAC-SHA256(secret_key, "divvy-refresh-token-v1")} – wie {@link LoginAttemptKeys} aus dem
 *       JWT-Secret abgeleitet, mit eigenem Label. Eine Rotation des Secrets macht alle Refresh Tokens ungültig.</li>
 * </ul>
 */
@Component
public class RefreshTokenHasher {

    private static final String HMAC = "HmacSHA256";
    private static final String PEPPER_LABEL = "divvy-refresh-token-v1";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;

    public RefreshTokenHasher(@Value("${secret_key}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("secret_key must be set (used to derive the refresh token pepper)");
        }
        this.pepper = hmac(secretKey.getBytes(StandardCharsets.UTF_8), PEPPER_LABEL);
    }

    /** Neuer zufälliger Refresh Token (Base64url ohne Padding, 43 Zeichen). */
    public String newToken() {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Speicher-Hash des Tokens (Hex, 64 Zeichen). */
    public String hash(String token) {
        return HexFormat.of().formatHex(hmac(pepper, token));
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
