/**
 * Unterstützte Gruppenwährungen (ISO 4217, alle mit zwei Nachkommastellen; DIV-77), Reihenfolge der Story – auch für
 * Auswahllisten. Gegenstück im Backend: `constants/SupportedCurrency.java`; beide Listen pinnt je ein Test
 * (`currency.spec.ts`, `SupportedCurrencySpec`), synchron gehalten von Hand. Neue Währungen hinten anhängen und erst nach
 * dem Backend ausrollen; ältere App-Versionen behalten unbekannte Codes beim Bearbeiten (group-form.ts).
 */
export const SUPPORTED_CURRENCIES = ['EUR', 'USD', 'GBP', 'CHF', 'CAD', 'AUD', 'MXN', 'BRL', 'PLN', 'CZK', 'NOK', 'SEK'] as const;

export type CurrencyCode = typeof SUPPORTED_CURRENCIES[number];

/** Standardwährung neuer Gruppen und Rückfall für Daten ohne Währung (z. B. gespeicherte aktive Gruppe von vor DIV-77). */
export const DEFAULT_CURRENCY: CurrencyCode = 'EUR';

export function isSupportedCurrency(code: unknown): code is CurrencyCode {
  return typeof code === 'string' && (SUPPORTED_CURRENCIES as readonly string[]).includes(code);
}
