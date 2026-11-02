import { StatisticsCategoryDto, StatisticsDto, StatisticsPeriod, StatisticsTimelineEntryDto } from './model/statistics-dto';

/** Höchstens so viele Kategorien erscheinen einzeln im Donut, der Rest wird zu „Sonstige“ (K8). */
export const DONUT_MAX_CATEGORIES = 8;
/** Farbe von „Sonstige“ und der darin gebündelten Kategorien; 0–7 sind die einzelnen Segmente. */
export const DONUT_OTHER_COLOR_INDEX = DONUT_MAX_CATEGORIES;

export interface DonutSegment {
  /** null bei „Sonstige“ (Name übersetzt die Seite) */
  name: string | null;
  amount: number;
  /** Anteil an der Donut-Summe in Prozent, eine Nachkommastelle, nur zur Anzeige */
  percent: number;
  colorIndex: number;
  other: boolean;
}

export interface CategoryRow {
  name: string;
  amount: number;
  settlement: boolean;
  /** null für Zeilen ohne Donut-Segment (Ausgleichszahlungen, negative Kategorien) */
  percent: number | null;
  colorIndex: number | null;
}

/**
 * Donut (K1, K8): nur positive Kategorien ohne Ausgleichszahlungen, in Backend-Reihenfolge (Betrag absteigend). Bei
 * mehr als 8 bleiben die 8 betragsstärksten einzeln, der Rest wird ein Segment „Sonstige“. Leer, wenn es keine
 * positiven Kategorien gibt.
 */
export function donutSegments(categories: StatisticsCategoryDto[]): DonutSegment[] {
  const positive = donutCategories(categories);
  const total = sumCents(positive) / 100;
  const single = positive.slice(0, DONUT_MAX_CATEGORIES).map((category, index): DonutSegment => ({
    name: category.name, amount: category.amount, percent: percentOf(category.amount, total),
    colorIndex: index, other: false,
  }));
  const rest = positive.slice(DONUT_MAX_CATEGORIES);
  if (rest.length === 0) return single;
  const otherAmount = sumCents(rest) / 100;
  return [...single, {
    name: null, amount: otherAmount, percent: percentOf(otherAmount, total),
    colorIndex: DONUT_OTHER_COLOR_INDEX, other: true,
  }];
}

/**
 * Liste unter dem Donut: alle Zeilen in Backend-Reihenfolge, auch Ausgleichszahlungen und negative Kategorien. Die
 * Summe der Liste ist „Von dir bezahlt“. Prozent und Farbe nur für Kategorien, die im Donut stecken (gebündelte mit
 * der Farbe von „Sonstige“).
 */
export function categoryRows(categories: StatisticsCategoryDto[]): CategoryRow[] {
  const positive = donutCategories(categories);
  const total = sumCents(positive) / 100;
  return categories.map(category => {
    const index = positive.indexOf(category);
    if (index < 0) {
      return { ...category, percent: null, colorIndex: null };
    }
    return {
      ...category,
      percent: percentOf(category.amount, total),
      colorIndex: Math.min(index, DONUT_OTHER_COLOR_INDEX),
    };
  });
}

/**
 * Beschriftung der Balken: MONTH Kalenderwoche über `weekLabel` (übersetzt), YEAR Monatskürzel der Sprache, ALL Jahr.
 */
export function timelineLabels(
  period: StatisticsPeriod,
  timeline: StatisticsTimelineEntryDto[],
  lang: string,
  weekLabel: (week: number) => string
): string[] {
  const monthFormat = new Intl.DateTimeFormat(lang, { month: 'short', timeZone: 'UTC' });
  return timeline.map(entry => {
    switch (period) {
      case 'MONTH': return weekLabel(entry.week ?? 0);
      case 'YEAR': return monthFormat.format(new Date(Date.UTC(2000, (entry.month ?? 1) - 1, 1)));
      default: return String(entry.year);
    }
  });
}

/**
 * Auswählbare Jahre: verfügbare Jahre, aktuelles Jahr und das gewählte Jahr (bleibt nach einem Gruppenwechsel
 * sichtbar, Codex-Review 2 F-001), absteigend und ohne Duplikate.
 */
export function yearOptions(availableYears: number[], currentYear: number, selectedYear: number): number[] {
  return [...new Set([...availableYears, currentYear, selectedYear])].sort((a, b) => b - a);
}

/**
 * Gibt es persönliche Ausgaben für die Diagramme? Nein, wenn die Zeitreihe nur 0 enthält und keine Kategoriezeile
 * existiert – unabhängig von den Gesamtausgaben (Codex F-002). Ja auch dann, wenn sich Zeilen zu 0 aufheben.
 */
export function hasPersonalSpendings(dto: StatisticsDto | null): boolean {
  if (!dto) return false;
  return dto.categories.length > 0 || dto.timeline.some(entry => entry.amount !== 0);
}

function donutCategories(categories: StatisticsCategoryDto[]): StatisticsCategoryDto[] {
  return categories.filter(category => !category.settlement && category.amount > 0);
}

/** Summe in Cent, damit sich Rundungsfehler von Gleitkommazahlen nicht aufaddieren. */
function sumCents(categories: StatisticsCategoryDto[]): number {
  return categories.reduce((sum, category) => sum + Math.round(category.amount * 100), 0);
}

function percentOf(amount: number, total: number): number {
  return total > 0 ? Math.round(amount / total * 1000) / 10 : 0;
}
