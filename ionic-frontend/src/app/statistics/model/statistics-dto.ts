export type StatisticsPeriod = 'MONTH' | 'YEAR' | 'ALL';

/** Ansicht der Statistikseite (DIV-72): nur Frontend-Zustand, kein Request-Parameter. */
export type StatisticsPerspective = 'PERSONAL' | 'GROUP';

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
  /** persönliche Zeitreihe: von dir bezahlt; `groupView`: alle Zahler; kann negativ sein */
  amount: number;
}

/** Backend `StatisticsCategoryDto`; netto, kann negativ sein. */
export interface StatisticsCategoryDto {
  name: string;
  amount: number;
  /** Ausgleichszahlungen (alle Sprachen in einer Zeile); Name übersetzt das Frontend */
  settlement: boolean;
}

/** Backend `StatisticsGroupViewDto`: alle Carts der gewählten Gruppen im Zeitraum, unabhängig vom Zahler (DIV-72). */
export interface StatisticsGroupViewDto {
  /** Carts im Zeitraum ohne Ausgleichszahlungen */
  cartCount: number;
  /** Gesamtausgaben / cartCount, auf Cent gerundet; 0 ohne Carts */
  averagePerCart: number;
  /** aktuelle Personen (Owner und Mitglieder) der gewählten Gruppen, jede einmal */
  memberCount: number;
  /** gleiche Einträge wie `StatisticsDto.timeline` */
  timeline: StatisticsTimelineEntryDto[];
  /** Betrag absteigend, ohne 0-Zeilen */
  categories: StatisticsCategoryDto[];
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
  /** persönlich */
  timeline: StatisticsTimelineEntryDto[];
  /** persönlich; Betrag absteigend, ohne 0-Zeilen */
  categories: StatisticsCategoryDto[];
  /** Gruppenperspektive (DIV-72) */
  groupView: StatisticsGroupViewDto;
}
