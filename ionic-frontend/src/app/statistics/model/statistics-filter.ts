import { CurrencyCode } from 'src/app/constants/currency';
import { StatisticsPeriod } from './statistics-dto';

export interface StatisticsFilter {
  /** null = alle Gruppen */
  groupId: number | null;
  period: StatisticsPeriod;
  /** bei ALL ignoriert */
  year: number;
  /** 1–12, nur bei MONTH verwendet */
  month: number;
  /**
   * Währung bei „Alle Gruppen“ (DIV-77): Beträge verschiedener Währungen werden nie addiert. `null` = Währung der aktiven
   * Gruppe (Standard und Rückfall, wenn die gewählte Währung unter den Gruppen nicht mehr vorkommt). Mit `groupId` ohne
   * Wirkung – dann gilt die Gruppenwährung.
   */
  currency: CurrencyCode | null;
}

/** Standard beim ersten Öffnen und nach einem Sitzungswechsel (AC2): alle Gruppen, aktuelles Jahr. */
export function defaultStatisticsFilter(now: Date): StatisticsFilter {
  return { groupId: null, period: 'YEAR', year: now.getFullYear(), month: now.getMonth() + 1, currency: null };
}

/**
 * Filter für den Sprung von der Übersicht (DIV-67): gleiche Gruppe; jährliche Ansicht (alle Jahre) -> „Alle“,
 * monatliche Ansicht eines Jahres -> dieses Jahr. Der Monat bleibt aus dem bisherigen Filter (bei ALL/YEAR ohne Wirkung).
 */
export function statisticsFilterForOverview(
  current: StatisticsFilter, groupId: number, view: 'year' | 'month', year: number
): StatisticsFilter {
  return view === 'year'
    ? { ...current, groupId, period: 'ALL' }
    : { ...current, groupId, period: 'YEAR', year };
}
