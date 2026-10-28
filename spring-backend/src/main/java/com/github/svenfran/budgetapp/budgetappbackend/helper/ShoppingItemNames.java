package com.github.svenfran.budgetapp.budgetappbackend.helper;

import java.util.Locale;

/**
 * Vergleich von Eintragsnamen (DIV-23): gleich ist, was nach trim und Kleinschreibung übereinstimmt („Milch“ =
 * „ milch “). Keine semantische Erkennung („Vollmilch“ ≠ „Milch“).
 */
public final class ShoppingItemNames {

    private ShoppingItemNames() {
    }

    /** Vergleichsschlüssel des Namens; {@code null} für einen fehlenden oder leeren Namen (mit nichts vergleichbar). */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
