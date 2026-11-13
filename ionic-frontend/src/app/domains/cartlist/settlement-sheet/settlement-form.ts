import { ValidatorFn, Validators } from '@angular/forms';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { UserDto } from 'src/app/model/user-dto';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import { SettlementPaymentDto } from '../model/settlement-payment-dto';

/**
 * Formularlogik der Ausgleichszahlung (DIV-70), verhaltensgleich aus der früheren SettlementPaymentPage übernommen.
 */

/** Betrag: Pflichtfeld, Dezimaltrennung nur mit Punkt; 0 und negative Werte bleiben erlaubt (wie bisher). */
export const AMOUNT_VALIDATORS: ValidatorFn[] = [
  Validators.required,
  Validators.pattern('[+-]?([0-9]*[.])?[0-9]+'),
];

/** Formularwerte, wenn das Formular gültig ist; `datePurchased` als ISO-Tag `YYYY-MM-DD`. */
export interface SettlementFormValue {
  amount: number;
  memberId: number;
  datePurchased: string;
}

/**
 * Wählbare Empfänger: Owner zuerst, dann die Mitglieder, jeweils ohne den eigenen Nutzer. Ohne Mitglieder (`members`
 * leer: der Owner ist allein, oder die Mitglieder sind noch nicht geladen) gibt es keinen Empfänger – wie bisher, da
 * ein Mitglied selbst immer in `members` steht.
 */
export function settlementRecipients(groupMembers: GroupMembers, userId: number | undefined): UserDto[] {
  if (groupMembers.members.length === 0) return [];
  const owner: UserDto = { id: groupMembers.ownerId, userName: groupMembers.ownerName };
  return [owner, ...groupMembers.members].filter(member => member.id !== userId);
}

/**
 * Ob ein Tag im Datums-Picker wählbar ist: Er liegt in einem Mitgliedschaftszeitraum des eigenen Nutzers in der Gruppe
 * (Grenzen einschließlich, offenes Ende erlaubt). Verglichen wird wie bisher der UTC-Kalendertag.
 */
export function isDaySelectable(isoDay: string, zeitraeume: Zeitraum[], userId: number | undefined, groupId: number): boolean {
  const day = utcDay(isoDay);
  return zeitraeume.some(zeitraum =>
    zeitraum.userId === userId &&
    zeitraum.groupId === groupId &&
    day >= utcDay(zeitraum.startDate) &&
    (!zeitraum.endDate || day <= utcDay(zeitraum.endDate))
  );
}

/**
 * Request der Ausgleichszahlung. `datePurchased` ist wie bisher Mitternacht UTC des gewählten Tages. Ist der Empfänger
 * nicht mehr in `recipients` (Liste inzwischen neu geladen), bleibt sein Name leer; das Backend prüft die Mitgliedschaft.
 */
export function toSettlementPaymentDto(value: SettlementFormValue, groupId: number, recipients: UserDto[]): SettlementPaymentDto {
  const recipient = recipients.find(member => member.id === value.memberId);
  return {
    amount: value.amount,
    groupId,
    member: { id: value.memberId, userName: recipient?.userName ?? '' },
    datePurchased: new Date(value.datePurchased),
  };
}

function utcDay(value: Date | string): string {
  return new Date(value).toISOString().substring(0, 10);
}
