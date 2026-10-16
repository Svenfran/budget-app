package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.util.Locale;

/**
 * Single place for e-mail normalization. Addresses are stored and looked up in
 * normalized form, so {@code Max@Example.de } and {@code max@example.de} are the
 * same user.
 */
public final class EmailUtils {

    private EmailUtils() {
    }

    /**
     * Trims and lower-cases an e-mail address. {@link Locale#ROOT} is mandatory:
     * with a Turkish default locale {@code "I".toLowerCase()} would yield a dotless i.
     */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
// CI-Prüfung DIV-37: nur Backend
