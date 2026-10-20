import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { calculateNextExecutionDate } from './next-execution-date';

describe('calculateNextExecutionDate', () => {
  // Monate in JS-Dates sind 0-basiert; day() hält die Tabellen lesbar.
  const day = (year: number, month: number, date: number) => new Date(year, month - 1, date);

  describe('monatlich ab dem 31.01.', () => {
    const cases: [string, Date, Date][] = [
      ['kappt im Februar auf das Monatsende', day(2025, 2, 1), day(2025, 2, 28)],
      ['kehrt im März auf den 31. zurück', day(2025, 3, 1), day(2025, 3, 31)],
      ['kappt im April auf den 30.', day(2025, 4, 1), day(2025, 4, 30)],
    ];
    cases.forEach(([description, today, expected]) => {
      it(description, () => {
        expect(calculateNextExecutionDate(day(2025, 1, 31), RECURRENCE_TYPE.MONTHLY, today)).toEqual(expected);
      });
    });

    it('trifft im Schaltjahr den 29.02.', () => {
      expect(calculateNextExecutionDate(day(2024, 1, 31), RECURRENCE_TYPE.MONTHLY, day(2024, 2, 1)))
        .toEqual(day(2024, 2, 29));
    });
  });

  describe('jährlich ab dem 29.02.2024', () => {
    it('kappt in Nicht-Schaltjahren auf den 28.02.', () => {
      expect(calculateNextExecutionDate(day(2024, 2, 29), RECURRENCE_TYPE.YEARLY, day(2024, 3, 1)))
        .toEqual(day(2025, 2, 28));
    });

    it('trifft im nächsten Schaltjahr wieder den 29.02.', () => {
      expect(calculateNextExecutionDate(day(2024, 2, 29), RECURRENCE_TYPE.YEARLY, day(2027, 3, 1)))
        .toEqual(day(2028, 2, 29));
    });
  });

  it('täglich: erster Tag nach heute', () => {
    expect(calculateNextExecutionDate(day(2025, 6, 15), RECURRENCE_TYPE.DAILY, day(2025, 6, 20)))
      .toEqual(day(2025, 6, 21));
  });

  it('wöchentlich: erste Woche nach heute', () => {
    expect(calculateNextExecutionDate(day(2025, 6, 15), RECURRENCE_TYPE.WEEKLY, day(2025, 6, 20)))
      .toEqual(day(2025, 6, 22));
  });

  it('wirft ohne Wiederholung', () => {
    expect(() => calculateNextExecutionDate(day(2025, 6, 15), RECURRENCE_TYPE.NONE, day(2025, 6, 20)))
      .toThrowError('Unsupported recurrence type: NONE');
  });
});
