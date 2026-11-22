import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { Cart } from '../model/cart';
import { CartFilter } from '../model/cart-filter';
import { toIsoDay } from '../iso-day';

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

/**
 * Kalendertag eines Kaufdatums für den Zeitraumfilter, so wie die Liste ihn anzeigt (date-Pipe): ein reines Datum
 * `YYYY-MM-DD` (liefert das Backend seit DIV-81) unverändert – `new Date(...)` läse es als Mitternacht UTC und verschöbe
 * es westlich von UTC auf den Vortag –, alles andere (Zeitstempel eines alten Backends, `Date`) als lokaler Kalendertag.
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

/**
 * Wendet einen Ausgabenfilter auf die vollständige Liste an (DIV-71; seit DIV-74 auch nach jeder erfolgreichen Änderung):
 * Titel und Beschreibung als Teilstring ohne Groß-/Kleinschreibung, Kategorien und Ersteller exakt nach Name, Zeitraum
 * nach Kalendertag wie in der Liste (beide Grenzen einschließlich). Leere Kriterien filtern nicht.
 */
export function applyCartFilter(carts: Cart[], cartFilter: CartFilter): Cart[] {
  const startDay = cartFilter.startDate ? toIsoDay(cartFilter.startDate) : null;
  const endDay = cartFilter.endDate ? toIsoDay(cartFilter.endDate) : null;
  return carts.filter(cart =>
    (cartFilter.title ? cart.title?.toLowerCase().includes(cartFilter.title.toLowerCase()) : true) &&
    (cartFilter.description ? cart.description?.toLowerCase().includes(cartFilter.description.toLowerCase()) : true) &&
    (cartFilter.category && cartFilter.category.length > 0 ? cartFilter.category.includes(cart.categoryDto.name) : true) &&
    (cartFilter.userName && cartFilter.userName.length > 0 ? cartFilter.userName.includes(cart.userDto.userName) : true) &&
    (startDay ? purchaseDayOf(cart.datePurchased) >= startDay : true) &&
    (endDay ? purchaseDayOf(cart.datePurchased) <= endDay : true)
  );
}
