/**
 * Cent-Rundung von Beträgen (DIV-77) mit derselben Semantik wie das Backend (`CostShareCalculator.toCents`:
 * `BigDecimal.valueOf(amount).setScale(2, HALF_UP)`): gerundet wird die kürzeste Dezimaldarstellung des Betrags
 * (`String(amount)` wie Javas `Double.toString`), kaufmännisch vom Nullpunkt weg. Deshalb nicht
 * `Math.round(amount * 100)` – das liefert z. B. `1.005 → 100` (Gleitkomma `100.49999…`) und `-0.005 → 0`.
 * Alle unterstützten Währungen haben zwei Nachkommastellen.
 */

/** Betrag in Cent, HALF_UP vom Nullpunkt weg: `1.005 → 101`, `-1.005 → -101`, `-0.004 → 0`. Nie `-0`. */
export function toCents(amount: number): number {
  if (!Number.isFinite(amount)) return amount;
  const { integer, fraction } = plainDigits(String(Math.abs(amount)));
  // Ganzzahl-Arithmetik auf den Ziffern: exakt, solange der Betrag in Cent unter Number.MAX_SAFE_INTEGER liegt
  // (deckt den Bereich des Backends ab); darüber nur noch näherungsweise, aber ohne Fehler.
  const roundUp = fraction.length > 2 && fraction[2] >= '5' ? 1 : 0;
  const result = Number(integer || '0') * 100 + Number(fraction.padEnd(2, '0').substring(0, 2)) + roundUp;
  return amount < 0 && result !== 0 ? -result : result;
}

/** Betrag auf ganze Cent gerundet (z. B. `65.349875 → 65.35`); `-0` wird zu `0`. */
export function roundToCents(amount: number): number {
  return toCents(amount) / 100 || 0;
}

/**
 * Gültige Betragseingabe als Text: optional Vorzeichen, Punkt oder Komma als Dezimaltrenner (das Textfeld der
 * Ausgleichszahlung liefert die Eingabe roh, die App formatiert deutsch), Trennzeichen am Ende erlaubt („45.“, „45,“).
 */
const AMOUNT_TEXT = /^[+-]?(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)$/;

/** Betrag aus einer Texteingabe (Komma wie Punkt); `null`, wenn kein gültiger Betrag. */
export function parseAmountText(value: string): number | null {
  const text = value.trim();
  return AMOUNT_TEXT.test(text) ? Number(text.replace(',', '.')) : null;
}

/**
 * Betrag als Text im Eingabefeld: zwei Nachkommastellen mit Komma wie alle Betragsanzeigen der App (Zahlenformat fest
 * `de`, DIV-77 A2), z. B. `42.45 → "42,45"`, `12 → "12,00"`, `-12 → "-12,00"`. Für das Vorbelegen (Bearbeiten,
 * Duplizieren) und nach dem Runden.
 */
export function amountInputText(amount: number): string {
  return roundToCents(amount).toFixed(2).replace('.', ',');
}

/**
 * Betrag aus dem Eingabefeld für den Request: Zahl (Punkt), egal ob als Zahl, mit Punkt oder mit Komma eingegeben;
 * `null`, wenn kein gültiger Betrag. Das Backend erwartet eine Zahl – „42,45“ als Text wäre dort ungültig.
 */
export function amountFromInput(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  return typeof value === 'string' ? parseAmountText(value) : null;
}

/**
 * Betragsfelder (Cart-Formular, Ausgleichszahlung) beim Verlassen und vor dem Absenden (DIV-77, D9): jeder gültige
 * Betrag auf Cent gerundet, als Text mit Komma und genau zwei Nachkommastellen (`amountInputText`), z. B.
 * `0.0000 → "0,00"`, `45.40349 → "45,40"`, `42.45 → "42,45"`. Zahlen werden direkt gerundet (ihre Textform kann
 * Exponentenschreibweise sein: `0.0000001` → `"1e-7"`, Codex F-002). Leer oder ungültig: `null`, das Feld bleibt
 * unverändert und die Validatoren melden wie bisher.
 */
export function formattedAmountInput(value: unknown): string | null {
  const amount = amountFromInput(value);
  return amount === null ? null : amountInputText(amount);
}

/** Ziffern vor und nach dem Komma, auch für Exponentenschreibweise (`1e-7`, `1.5e+21`). */
function plainDigits(text: string): { integer: string; fraction: string } {
  const [mantissa, exponentText] = text.toLowerCase().split('e');
  const exponent = exponentText ? Number(exponentText) : 0;
  const [intPart, fracPart = ''] = mantissa.split('.');
  const digits = intPart + fracPart;
  const point = intPart.length + exponent;
  if (point <= 0) return { integer: '', fraction: '0'.repeat(-point) + digits };
  if (point >= digits.length) return { integer: digits + '0'.repeat(point - digits.length), fraction: '' };
  return { integer: digits.substring(0, point), fraction: digits.substring(point) };
}
