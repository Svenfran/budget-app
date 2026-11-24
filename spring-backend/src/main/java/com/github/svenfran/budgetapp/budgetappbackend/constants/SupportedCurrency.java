package com.github.svenfran.budgetapp.budgetappbackend.constants;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UnsupportedCurrencyException;

/**
 * Unterstützte Gruppenwährungen (ISO 4217, alle mit zwei Nachkommastellen; DIV-77). Reihenfolge = Reihenfolge der Story,
 * auch für Auswahllisten; neue Währungen hinten anhängen (NOK, SEK nachträglich in DIV-77). Reihenfolge beim Ausrollen:
 * erst das Backend, dann die App (eine App mit einer neuen Währung bekäme von einem alten Backend 400). Gegenstück im Frontend: {@code ionic-frontend/src/app/constants/currency.ts} – beide Listen
 * werden je von einem Test gepinnt und von Hand synchron gehalten.
 */
public enum SupportedCurrency {
    EUR, USD, GBP, CHF, CAD, AUD, MXN, BRL, PLN, CZK, NOK, SEK;

    public static final SupportedCurrency DEFAULT = EUR;

    /**
     * Code aus einem Request: {@code null} bleibt {@code null} (Feld nicht gesendet, z. B. alte App-Versionen), ein
     * unbekannter Code oder falsche Schreibweise (nur Großbuchstaben) führt zu {@link UnsupportedCurrencyException}.
     */
    public static SupportedCurrency fromCode(String code) throws UnsupportedCurrencyException {
        if (code == null) return null;
        for (SupportedCurrency currency : values()) {
            if (currency.name().equals(code)) return currency;
        }
        throw new UnsupportedCurrencyException("Currency " + code + " is not supported");
    }
}
