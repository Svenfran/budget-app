import { StatisticsCategoryDto, StatisticsDto, StatisticsTimelineEntryDto } from './model/statistics-dto';
import {
  categoryRows, DONUT_OTHER_COLOR_INDEX, donutSegments, hasPersonalSpendings, timelineLabels, yearOptions,
} from './statistics-calc';

describe('statistics-calc', () => {
  const category = (name: string, amount: number, settlement = false): StatisticsCategoryDto => ({ name, amount, settlement });
  /** n positive Kategorien mit absteigenden Beträgen n, n-1, …, 1 */
  const categories = (n: number) => Array.from({ length: n }, (_, i) => category(`K${i + 1}`, n - i));
  const cents = (amounts: number[]) => amounts.reduce((sum, amount) => sum + Math.round(amount * 100), 0);
  const entry = (overrides: Partial<StatisticsTimelineEntryDto>): StatisticsTimelineEntryDto =>
    ({ year: 2025, month: null, week: null, from: null, to: null, amount: 0, ...overrides });
  const dto = (overrides: Partial<StatisticsDto>): StatisticsDto => ({
    groupId: null, period: 'YEAR', year: 2025, month: null, totalSpent: 0, paidByUser: 0, currentBalance: 0,
    availableYears: [], timeline: [], categories: [], ...overrides,
  });

  /** Lebensmittel 60, Haushalt 20, Ausgleichszahlung −30, Erstattung −10 → bezahlt 40, Donut-Summe 80 */
  const mixed = [category('Lebensmittel', 60), category('Haushalt', 20), category('Ausgleichszahlung', -30, true),
    category('Erstattung', -10)];

  describe('Donut und Kategorieliste (K1, AC9)', () => {
    it('Donut enthält nur positive Kategorien ohne Ausgleichszahlungen, Prozent relativ zur Donut-Summe', () => {
      const segments = donutSegments(mixed);

      expect(segments.map(s => [s.name, s.amount, s.percent, s.colorIndex, s.other])).toEqual([
        ['Lebensmittel', 60, 75, 0, false],
        ['Haushalt', 20, 25, 1, false],
      ]);
    });

    it('Liste zeigt alle Zeilen, Ausgleichszahlungen am Ende; Ausgleichszahlungen und negative Zeilen ohne Prozent und Farbe', () => {
      const rows = categoryRows(mixed);

      expect(rows.map(r => [r.name, r.amount, r.settlement, r.percent, r.colorIndex])).toEqual([
        ['Lebensmittel', 60, false, 75, 0],
        ['Haushalt', 20, false, 25, 1],
        ['Erstattung', -10, false, null, null],
        ['Ausgleichszahlung', -30, true, null, null],
      ]);
      expect(cents(rows.map(r => r.amount))).withContext('Summe der Liste = Von dir bezahlt').toBe(4000);
    });

    it('Ausgleichszahlungen stehen auch mit dem größten Betrag am Ende, sonst bleibt die Backend-Reihenfolge', () => {
      const backendOrder = [category('Ausgleichszahlung', 500, true), category('Lebensmittel', 60),
        category('Haushalt', 20), category('Erstattung', -10)];

      const rows = categoryRows(backendOrder);

      expect(rows.map(r => r.name)).toEqual(['Lebensmittel', 'Haushalt', 'Erstattung', 'Ausgleichszahlung']);
      expect(rows.map(r => [r.percent, r.colorIndex])).toEqual([[75, 0], [25, 1], [null, null], [null, null]]);
    });

    it('rundet Prozente auf eine Nachkommastelle; die Summe ist ungefähr 100', () => {
      const segments = donutSegments([category('A', 1), category('B', 1), category('C', 1)]);

      expect(segments.map(s => s.percent)).toEqual([33.3, 33.3, 33.3]);
      expect(segments.reduce((sum, s) => sum + s.percent, 0)).toBeCloseTo(100, 0);
    });

    it('ohne positive Kategorien ist der Donut leer, die Liste bleibt', () => {
      const onlyNegative = [category('Ausgleichszahlung', 15, true), category('Erstattung', -5)];

      expect(donutSegments(onlyNegative)).toEqual([]);
      expect(categoryRows(onlyNegative).length).toBe(2);
      expect(donutSegments([])).toEqual([]);
      expect(categoryRows([])).toEqual([]);
    });
  });

  describe('Bündelung „Sonstige“ (K8)', () => {
    it('8 Kategorien erscheinen einzeln, ohne „Sonstige“', () => {
      const segments = donutSegments(categories(8));

      expect(segments.length).toBe(8);
      expect(segments.some(s => s.other)).toBeFalse();
      expect(segments.map(s => s.colorIndex)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    });

    it('9 Kategorien: 8 einzeln, „Sonstige“ aus der kleinsten', () => {
      const segments = donutSegments(categories(9));

      expect(segments.length).toBe(9);
      expect(segments[8]).toEqual({ name: null, amount: 1, percent: 2.2, colorIndex: DONUT_OTHER_COLOR_INDEX, other: true });
    });

    it('12 Kategorien: höchstens 9 Segmente, Segmentsumme = Donut-Summe; die Liste zeigt alle einzeln', () => {
      const input = categories(12);
      const segments = donutSegments(input);
      const rows = categoryRows(input);

      expect(segments.length).toBe(9);
      expect(segments[8].amount).toBe(4 + 3 + 2 + 1);
      expect(cents(segments.map(s => s.amount))).toBe(cents(input.map(c => c.amount)));
      expect(segments.reduce((sum, s) => sum + s.percent, 0)).toBeCloseTo(100, 0);
      expect(rows.length).toBe(12);
      expect(rows.slice(8).map(r => r.colorIndex)).withContext('gebündelte Zeilen in der Farbe von „Sonstige“')
        .toEqual([DONUT_OTHER_COLOR_INDEX, DONUT_OTHER_COLOR_INDEX, DONUT_OTHER_COLOR_INDEX, DONUT_OTHER_COLOR_INDEX]);
      expect(rows.every(r => r.percent !== null)).toBeTrue();
    });
  });

  describe('Leerzustand der Diagramme (Codex F-002)', () => {
    it('ohne eigene Carts gibt es keine persönlichen Ausgaben, auch wenn andere bezahlt haben', () => {
      const othersPaid = dto({ totalSpent: 80, paidByUser: 0, timeline: [entry({ month: 1 }), entry({ month: 2 })] });

      expect(hasPersonalSpendings(othersPaid)).toBeFalse();
      expect(hasPersonalSpendings(null)).toBeFalse();
    });

    it('netto 0 durch +100 € Lebensmittel und −100 € Ausgleichszahlung zählt als persönliche Ausgaben', () => {
      const netZero = dto({
        paidByUser: 0, timeline: [entry({ month: 1, amount: 0 })],
        categories: [category('Lebensmittel', 100), category('Ausgleichszahlung', -100, true)],
      });

      expect(hasPersonalSpendings(netZero)).toBeTrue();
      expect(categoryRows(netZero.categories).length).toBe(2);
    });

    it('eine Zeitreihe ungleich 0 zählt als persönliche Ausgaben', () => {
      expect(hasPersonalSpendings(dto({ timeline: [entry({ month: 3, amount: -20 })] }))).toBeTrue();
    });
  });

  describe('Beschriftungen', () => {
    const weekLabel = (week: number) => `KW ${week}`;

    it('Monat: Kalenderwochen über die übersetzte Beschriftung', () => {
      const timeline = [entry({ year: 2026, week: 53 }), entry({ year: 2027, week: 1 })];

      expect(timelineLabels('MONTH', timeline, 'de', weekLabel)).toEqual(['KW 53', 'KW 1']);
    });

    it('Jahr: Monatskürzel in der Sprache der App', () => {
      const timeline = [entry({ month: 1 }), entry({ month: 3 }), entry({ month: 12 })];

      expect(timelineLabels('YEAR', timeline, 'en', weekLabel)).toEqual(['Jan', 'Mar', 'Dec']);
      expect(timelineLabels('YEAR', timeline, 'de', weekLabel)[1]).withContext('Kürzel hängt von der ICU-Version ab').toMatch(/^Mär/);
    });

    it('Alle: Jahreszahl', () => {
      expect(timelineLabels('ALL', [entry({ year: 2023 }), entry({ year: 2024 })], 'de', weekLabel)).toEqual(['2023', '2024']);
    });
  });

  describe('Jahresauswahl (Codex-Review 2 F-001)', () => {
    it('enthält verfügbare Jahre, aktuelles und gewähltes Jahr absteigend ohne Duplikate', () => {
      expect(yearOptions([2025], 2026, 2024)).toEqual([2026, 2025, 2024]);
      expect(yearOptions([2026, 2025], 2026, 2025)).toEqual([2026, 2025]);
      expect(yearOptions([], 2026, 2026)).toEqual([2026]);
    });
  });
});
