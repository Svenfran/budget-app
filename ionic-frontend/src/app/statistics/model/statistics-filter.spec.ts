import { defaultStatisticsFilter, StatisticsFilter, statisticsFilterForOverview } from './statistics-filter';

describe('defaultStatisticsFilter', () => {
  it('startet ohne gewählte Währung (= Währung der aktiven Gruppe, DIV-77)', () => {
    expect(defaultStatisticsFilter(new Date('2025-06-15T00:00:00Z')).currency).toBeNull();
  });
});

describe('statisticsFilterForOverview', () => {
  const current: StatisticsFilter = { groupId: null, period: 'MONTH', year: 2025, month: 6, currency: null };

  it('übernimmt bei jährlicher Ansicht die Gruppe mit Zeitraum „Alle“', () => {
    expect(statisticsFilterForOverview(current, 3, 'year', 2024))
      .toEqual({ groupId: 3, period: 'ALL', year: 2025, month: 6, currency: null });
  });

  it('übernimmt bei monatlicher Ansicht die Gruppe und das gewählte Jahr', () => {
    expect(statisticsFilterForOverview(current, 7, 'month', 2024))
      .toEqual({ groupId: 7, period: 'YEAR', year: 2024, month: 6, currency: null });
  });
});
