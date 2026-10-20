import moment from 'moment';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';

const STEPS: Partial<Record<RECURRENCE_TYPE, [number, moment.unitOfTime.DurationConstructor]>> = {
  [RECURRENCE_TYPE.DAILY]: [1, 'days'],
  [RECURRENCE_TYPE.WEEKLY]: [1, 'weeks'],
  [RECURRENCE_TYPE.MONTHLY]: [1, 'months'],
  [RECURRENCE_TYPE.YEARLY]: [1, 'years'],
};

/**
 * Erster Termin eines wiederkehrenden Einkaufs nach heute (Vorschau, DIV-25). Jeder Schritt wird vom Ausgangsdatum
 * aus gerechnet: moment kappt auf das Monatsende, der Tag bleibt danach aber nicht auf dem gekappten Wert
 * (31.01. → 28.02. → 31.03.). Entspricht RecurrenceCalculator im Backend mit dem Tag des Ausgangsdatums als Anker.
 */
export function calculateNextExecutionDate(lastExecutionDate: Date, recurrenceType: RECURRENCE_TYPE, today: Date): Date {
  const step = STEPS[recurrenceType];
  if (!step) {
    throw new Error("Unsupported recurrence type: " + recurrenceType);
  }
  const [amount, unit] = step;

  let steps = 1;
  let next = moment(lastExecutionDate).add(amount, unit);
  while (!next.isAfter(today)) {
    steps++;
    next = moment(lastExecutionDate).add(amount * steps, unit);
  }
  return next.toDate();
}
