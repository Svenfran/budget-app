export type StatisticsPeriod = 'MONTH' | 'YEAR' | 'ALL';

/** Backend `StatisticsTimelineEntryDto`; Beträge in Euro. */
export interface StatisticsTimelineEntryDto {
  /** MONTH: wochenbasiertes ISO-Jahr, sonst Kalenderjahr */
  year: number;
  /** nur YEAR */
  month: number | null;
  /** nur MONTH: ISO-Kalenderwoche, `from`/`to` (yyyy-MM-dd) auf den Monat beschnitten */
  week: number | null;
  from: string | null;
  to: string | null;
  /** von dir bezahlt, kann negativ sein */
  amount: number;
}

/** Backend `StatisticsCategoryDto`; netto, kann negativ sein. */
export interface StatisticsCategoryDto {
  name: string;
  amount: number;
  /** Ausgleichszahlungen (alle Sprachen in einer Zeile); Name übersetzt das Frontend */
  settlement: boolean;
}

/** Backend `StatisticsDto` (GET /api/statistics). */
export interface StatisticsDto {
  /** null = alle Gruppen */
  groupId: number | null;
  period: StatisticsPeriod;
  year: number | null;
  month: number | null;
  totalSpent: number;
  paidByUser: number;
  /** Carts mit dir als Zahler im Zeitraum, ohne Ausgleichszahlungen */
  paidCartCount: number;
  /** unabhängig vom Zeitraum */
  currentBalance: number;
  /** absteigend */
  availableYears: number[];
  timeline: StatisticsTimelineEntryDto[];
  /** Betrag absteigend, ohne 0-Zeilen */
  categories: StatisticsCategoryDto[];
}
