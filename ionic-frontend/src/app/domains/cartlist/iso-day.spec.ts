import { formatDisplayDate, isoDayOf, toIsoDay } from './iso-day';

// Verschoben aus filter-sheet/cart-filter-form.spec.ts (DIV-70 Phase 4); feste lokale Daten, nie das aktuelle.
describe('iso-day', () => {
  it('formatDisplayDate: ISO-Tag → dd.MM.yyyy', () => {
    expect(formatDisplayDate('2025-06-05')).toBe('05.06.2025');
  });

  it('isoDayOf: Tagesanteil eines ion-datetime-Werts ohne Zeitzonenverschiebung', () => {
    expect(isoDayOf('2025-06-05')).toBe('2025-06-05');
    expect(isoDayOf('2025-06-05T23:59:00')).toBe('2025-06-05');
    expect(isoDayOf('2025-06-05T00:30:00+02:00')).toBe('2025-06-05');
  });

  it('toIsoDay: lokaler Kalendertag, zweistellig aufgefüllt', () => {
    expect(toIsoDay(new Date(2025, 0, 5, 23, 59))).toBe('2025-01-05');
  });
});
