/**
 * Kalendertage als ISO-Tag `YYYY-MM-DD` (lokaler Tag), wie ihn `ion-datetime` liefert und erwartet. Gemeinsam genutzt von
 * Ausgabenfilter (DIV-71), Ausgleichszahlung (DIV-70) und Ausgaben-Seite; aus filter-sheet/cart-filter-form.ts verschoben.
 */

/** ISO-Tag `YYYY-MM-DD` → Anzeige `dd.MM.yyyy` (bisheriges Anzeigeformat der Datumsfelder). */
export function formatDisplayDate(isoDay: string): string {
  const [year, month, day] = isoDay.split('-');
  return `${day}.${month}.${year}`;
}

/**
 * Tagesanteil eines `ion-datetime`-Werts (`YYYY-MM-DD` oder `YYYY-MM-DDTHH:mm…`). Bewusst ohne `new Date(...)`, damit
 * keine Zeitzonenverschiebung den Tag ändert.
 */
export function isoDayOf(datetimeValue: string): string {
  return datetimeValue.substring(0, 10);
}

/** Lokaler Kalendertag eines Datums als ISO-Tag. */
export function toIsoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
