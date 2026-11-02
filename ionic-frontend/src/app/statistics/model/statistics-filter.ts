import { StatisticsPeriod } from './statistics-dto';

export interface StatisticsFilter {
  /** null = alle Gruppen */
  groupId: number | null;
  period: StatisticsPeriod;
  /** bei ALL ignoriert */
  year: number;
  /** 1–12, nur bei MONTH verwendet */
  month: number;
}

/** Standard beim ersten Öffnen und nach einem Sitzungswechsel (AC2): alle Gruppen, aktuelles Jahr. */
export function defaultStatisticsFilter(now: Date): StatisticsFilter {
  return { groupId: null, period: 'YEAR', year: now.getFullYear(), month: now.getMonth() + 1 };
}
