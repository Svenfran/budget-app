import { FormControl, FormGroup } from '@angular/forms';
import { CartFilter } from '../model/cart-filter';
import {
  CartFilterFormValue, dateRangeValidator, formatDisplayDate, isoDayOf, toCartFilter, toFormValue, toIsoDay,
} from './cart-filter-form';

// DIV-71: Abbildung zwischen angewendetem Filter und Formular des Filter-Sheets. Feste lokale Daten, nie das aktuelle.
describe('cart-filter-form (DIV-71)', () => {
  const emptyForm: CartFilterFormValue = {
    title: null, description: null, categories: null, members: null, startDate: null, endDate: null,
  };
  const noCriteria: CartFilter = {
    title: null, description: null, category: null, startDate: null, endDate: null, userName: null,
  };

  describe('toFormValue', () => {
    it('leerer Filter → alle Formularfelder null', () => {
      expect(toFormValue({})).toEqual(emptyForm);
    });

    it('übernimmt alle Felder, Datum als lokaler ISO-Tag (auch über Monats- und Jahresgrenzen)', () => {
      const value = toFormValue({
        title: 'Edeka', description: 'Woche', category: ['Lebensmittel'], userName: ['Sven'],
        startDate: new Date(2024, 11, 31), endDate: new Date(2025, 0, 1),
      });

      expect(value).toEqual({
        title: 'Edeka', description: 'Woche', categories: ['Lebensmittel'], members: ['Sven'],
        startDate: '2024-12-31', endDate: '2025-01-01',
      });
    });

    it('kopiert die Auswahl-Arrays, statt sie mit dem angewendeten Filter zu teilen', () => {
      const filter: CartFilter = { category: ['Lebensmittel'], userName: ['Sven'] };

      const value = toFormValue(filter);
      value.categories!.push('Drogerie');
      value.members!.push('Anna');

      expect(filter.category).toEqual(['Lebensmittel']);
      expect(filter.userName).toEqual(['Sven']);
    });
  });

  describe('toCartFilter', () => {
    it('leeres Formular → Filter ohne Kriterium mit allen Feldern null', () => {
      expect(toCartFilter(emptyForm)).toEqual(noCriteria);
    });

    it('trimmt Titel und Beschreibung', () => {
      const filter = toCartFilter({ ...emptyForm, title: '  Edeka ', description: ' Woche  ' });

      expect(filter.title).toBe('Edeka');
      expect(filter.description).toBe('Woche');
    });

    it('leere oder nur aus Leerzeichen bestehende Texte → null (kein Kriterium)', () => {
      const filter = toCartFilter({ ...emptyForm, title: '   ', description: '' });

      expect(filter).toEqual(noCriteria);
    });

    it('leere Auswahl (alles abgewählt) → null (kein Kriterium)', () => {
      const filter = toCartFilter({ ...emptyForm, categories: [], members: [] });

      expect(filter).toEqual(noCriteria);
    });

    it('ISO-Tag → lokale Mitternacht dieses Tages', () => {
      const filter = toCartFilter({ ...emptyForm, startDate: '2025-06-01', endDate: '2025-06-30' });

      expect(filter.startDate).toEqual(new Date(2025, 5, 1));
      expect(filter.endDate).toEqual(new Date(2025, 5, 30));
    });

    it('kopiert die Auswahl-Arrays aus dem Formular', () => {
      const value: CartFilterFormValue = { ...emptyForm, categories: ['Lebensmittel'], members: ['Sven'] };

      const filter = toCartFilter(value);
      value.categories!.push('Drogerie');
      value.members!.push('Anna');

      expect(filter.category).toEqual(['Lebensmittel']);
      expect(filter.userName).toEqual(['Sven']);
    });
  });

  // Codex-Review 1, F-006: Gleichheit nur für vollständig normalisierte Filter, sonst das normalisierte Ergebnis
  describe('Hin- und Rückweg', () => {
    it('vollständig normalisierter Filter bleibt gleich', () => {
      const filter: CartFilter = {
        title: 'Edeka', description: 'Woche', category: ['Lebensmittel', 'Drogerie'],
        startDate: new Date(2025, 5, 1), endDate: new Date(2025, 5, 30), userName: ['Sven'],
      };

      expect(toCartFilter(toFormValue(filter))).toEqual(filter);
    });

    it('Filter ohne Kriterium bleibt gleich', () => {
      expect(toCartFilter(toFormValue(noCriteria))).toEqual(noCriteria);
    });

    it('partieller Filter (Schnellfilter) → fehlende Felder werden null', () => {
      expect(toCartFilter(toFormValue({ category: ['Lebensmittel'] })))
        .toEqual({ ...noCriteria, category: ['Lebensmittel'] });
    });

    it('Whitespace-Titel und leere Auswahl → null', () => {
      expect(toCartFilter(toFormValue({ title: '  ', category: [], userName: [] }))).toEqual(noCriteria);
    });

    it('Datum mit Uhrzeit → lokale Mitternacht desselben Tages', () => {
      const result = toCartFilter(toFormValue({ startDate: new Date(2025, 5, 1, 14, 30) }));

      expect(result.startDate).toEqual(new Date(2025, 5, 1));
    });
  });

  describe('dateRangeValidator', () => {
    const form = (startDate: string | null, endDate: string | null) => new FormGroup(
      { startDate: new FormControl(startDate), endDate: new FormControl(endDate) },
      { validators: dateRangeValidator },
    );

    it('„von“ nach „bis“ → Fehler dateRange', () => {
      expect(form('2025-06-30', '2025-06-01').errors).toEqual({ dateRange: true });
    });

    it('„von“ vor „bis“ → gültig', () => {
      expect(form('2025-06-01', '2025-06-30').valid).toBeTrue();
    });

    it('gleicher Tag → gültig', () => {
      expect(form('2025-06-15', '2025-06-15').valid).toBeTrue();
    });

    it('nur ein Datum oder keins gesetzt → gültig', () => {
      expect(form('2025-06-30', null).valid).toBeTrue();
      expect(form(null, '2025-06-01').valid).toBeTrue();
      expect(form(null, null).valid).toBeTrue();
    });

    it('vergleicht über Monats- und Jahresgrenzen korrekt', () => {
      expect(form('2025-01-01', '2024-12-31').errors).toEqual({ dateRange: true });
      expect(form('2025-09-30', '2025-10-01').valid).toBeTrue();
    });
  });

  describe('Datumshilfen', () => {
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
});
