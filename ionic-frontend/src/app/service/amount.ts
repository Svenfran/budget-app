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

/** Gültige Zahleneingabe (optional mit Vorzeichen, Punkt als Dezimaltrenner) mit mehr als zwei Nachkommastellen. */
const AMOUNT_WITH_MORE_THAN_TWO_DECIMALS = /^[+-]?[0-9]*\.[0-9]{3,}$/;

/**
 * Eingabe im Betragsfeld (DIV-77, D9): Ein gültiger Betrag mit mehr als zwei Nachkommastellen wird auf Cent gerundet
 * zurückgegeben; alles andere (leer, höchstens zwei Stellen, ungültiges Format) liefert `null` – das Feld bleibt dann
 * unverändert und die Validatoren melden wie bisher.
 */
export function roundedAmountInput(value: unknown): number | null {
  if (typeof value !== 'number' && typeof value !== 'string') return null;
  const text = String(value).trim();
  if (!AMOUNT_WITH_MORE_THAN_TWO_DECIMALS.test(text)) return null;
  return roundToCents(Number(text));
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
