import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { CartFilter } from '../model/cart-filter';

/**
 * Werte des Filter-Formulars (DIV-71). Datumswerte als ISO-Tag `YYYY-MM-DD` (lokaler Kalendertag): direkt kompatibel mit
 * `ion-datetime` (`value`/`min`/`max`) und per Stringvergleich sortierbar.
 */
export interface CartFilterFormValue {
  title: string | null;
  description: string | null;
  categories: string[] | null;
  members: string[] | null;
  startDate: string | null;
  endDate: string | null;
}

/** Angewendeter Filter → Formularwerte (Arrays kopiert, damit das Formular den angewendeten Filter nie verändert). */
export function toFormValue(filter: CartFilter): CartFilterFormValue {
  return {
    title: filter.title ?? null,
    description: filter.description ?? null,
    categories: filter.category ? [...filter.category] : null,
    members: filter.userName ? [...filter.userName] : null,
    startDate: filter.startDate ? toIsoDay(filter.startDate) : null,
    endDate: filter.endDate ? toIsoDay(filter.endDate) : null,
  };
}

/**
 * Formularwerte → anzuwendender Filter. Liefert immer alle Felder; leere Eingaben (leer/Whitespace, leere Auswahl)
 * werden zu `null` = kein Kriterium, damit `CartlistPage.filterCarts` einen Filter ohne Kriterium als „aufheben“ erkennt.
 * Datum als lokale Mitternacht des Tages (wie bisher `FilterModalPage.convertStringToDate`).
 */
export function toCartFilter(value: CartFilterFormValue): CartFilter {
  return {
    title: trimmedOrNull(value.title),
    description: trimmedOrNull(value.description),
    category: nonEmptyOrNull(value.categories),
    startDate: value.startDate ? fromIsoDay(value.startDate) : null,
    endDate: value.endDate ? fromIsoDay(value.endDate) : null,
    userName: nonEmptyOrNull(value.members),
  };
}

/** Formulargruppen-Validator: „Datum von“ darf nicht nach „Datum bis“ liegen (gleicher Tag ist gültig). */
export const dateRangeValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const start: string | null = group.get('startDate')?.value ?? null;
  const end: string | null = group.get('endDate')?.value ?? null;
  return start && end && start > end ? { dateRange: true } : null;
};

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

/**
 * Kalendertag eines Kaufdatums für den Zeitraumfilter, so wie die Liste ihn anzeigt (date-Pipe): ein reines Datum
 * `YYYY-MM-DD` (Backend, DATE-Spalte) unverändert – `new Date(...)` läse es als Mitternacht UTC und verschöbe es westlich
 * von UTC auf den Vortag –, alles andere (Zeitstempel, `Date`) als lokaler Kalendertag.
 */
export function purchaseDayOf(datePurchased: Date | string): string {
  if (typeof datePurchased === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(datePurchased)) {
    return datePurchased;
  }
  return toIsoDay(new Date(datePurchased));
}

function fromIsoDay(isoDay: string): Date {
  const [year, month, day] = isoDay.split('-').map(part => parseInt(part, 10));
  return new Date(year, month - 1, day);
}

function trimmedOrNull(text: string | null): string | null {
  const trimmed = text?.trim();
  return trimmed ? trimmed : null;
}

function nonEmptyOrNull(values: string[] | null): string[] | null {
  return values && values.length > 0 ? [...values] : null;
}
