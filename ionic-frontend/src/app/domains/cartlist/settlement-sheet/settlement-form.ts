import { ValidatorFn, Validators } from '@angular/forms';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { UserDto } from 'src/app/model/user-dto';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import { SettlementPaymentDto } from '../model/settlement-payment-dto';
import { Cart } from '../model/cart';
import { purchaseDayOf } from '../filter-sheet/cart-filter-form';

/**
 * Formularlogik der Ausgleichszahlung (DIV-70), verhaltensgleich aus der früheren SettlementPaymentPage übernommen.
 */

/**
 * Betrag: Pflichtfeld, größer als 0, Dezimaltrennung nur mit Punkt, ohne Vorzeichen (DIV-74; vorher waren 0 und negative
 * Werte erlaubt). Die Richtung ergibt sich aus Zahler und Empfänger, nicht aus dem Vorzeichen. Mehr als zwei
 * Nachkommastellen lehnt das Formular nicht mehr ab, sondern das Sheet rundet sie beim Verlassen des Feldes und vor dem
 * Speichern auf Cent (DIV-77, D9: `roundedAmountInput`); wird dabei auf 0 gerundet, greift `min`. Das Backend lehnt
 * weiterhin ab, was mit mehr als zwei Nachkommastellen ankommt.
 */
export const AMOUNT_VALIDATORS: ValidatorFn[] = [
  Validators.required,
  Validators.pattern('[0-9]*([.][0-9]+)?'),
  Validators.min(0.01),
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

/** Richtung beim Anlegen (DIV-74): „Ich habe gezahlt an …“ bzw. „Ich habe erhalten von …“. */
export type SettlementDirection = 'paid' | 'received';

export interface SettlementDtoOptions {
  /** Edit-Modus (DIV-74): Id des Settlements; `member` bleibt der Empfänger, die Richtung zählt nicht. */
  settlementId?: number;
  /**
   * Anlegen einer erhaltenen Zahlung: der angemeldete Nutzer. Die gewählte Gegenpartei (`memberId`) wird dann Zahler
   * (`payer`), der Nutzer Empfänger (`member`).
   */
  receivedBy?: UserDto;
}

/**
 * Request der Ausgleichszahlung. `datePurchased` ist wie bisher Mitternacht UTC des gewählten Tages. Ist die gewählte
 * Gegenpartei nicht mehr in `recipients` (Liste inzwischen neu geladen), bleibt ihr Name leer; das Backend prüft die
 * Mitgliedschaft. Ohne Optionen wie bisher: Zahler ist der angemeldete Nutzer (kein `payer`), `member` der Empfänger.
 */
export function toSettlementPaymentDto(value: SettlementFormValue, groupId: number, recipients: UserDto[],
                                       options: SettlementDtoOptions = {}): SettlementPaymentDto {
  const recipient = recipients.find(member => member.id === value.memberId);
  const counterpart: UserDto = { id: value.memberId, userName: recipient?.userName ?? '' };
  const received = options.settlementId === undefined && !!options.receivedBy;
  return {
    ...(options.settlementId !== undefined ? { id: options.settlementId } : {}),
    ...(received ? { payer: counterpart } : {}),
    amount: value.amount,
    groupId,
    member: received ? { id: options.receivedBy!.id, userName: options.receivedBy!.userName } : counterpart,
    datePurchased: new Date(value.datePurchased),
  };
}

/** Bestehende Ausgleichszahlung für den Edit-Modus des Sheets (DIV-74), aus dem Cart-Paar der Liste. */
export interface SettlementEditData {
  /** Id des Settlements. */
  id: number;
  /** Rolle des angemeldeten Nutzers: sein Cart ist der positive (Zahler) bzw. negative (Empfänger). */
  role: 'payer' | 'receiver';
  payer: UserDto;
  receiver: UserDto;
  /** Betrag > 0. */
  amount: number;
  /** ISO-Tag `YYYY-MM-DD`. */
  datePurchased: string;
}

/**
 * Edit-Daten aus einem eigenen Cart einer Ausgleichszahlung und dem Partner-Cart mit derselben `settlementId` in
 * `carts` (auch wenn der Partner-Cart als gelöscht markiert ist). Zahler ist der User des positiven Carts, Empfänger der
 * des negativen. `undefined`, wenn der Cart zu keinem Settlement gehört oder der Partner fehlt (veraltete Liste).
 */
export function settlementEditData(cart: Cart, carts: Cart[]): SettlementEditData | undefined {
  if (!cart.settlementId) return undefined;
  const partner = carts.find(c => c.settlementId === cart.settlementId && c.id !== cart.id);
  if (!partner) return undefined;
  const ownIsPayer = cart.amount > 0;
  const [payerCart, receiverCart] = ownIsPayer ? [cart, partner] : [partner, cart];
  return {
    id: cart.settlementId,
    role: ownIsPayer ? 'payer' : 'receiver',
    payer: { id: payerCart.userDto.id, userName: payerCart.userDto.userName },
    receiver: { id: receiverCart.userDto.id, userName: receiverCart.userDto.userName },
    amount: Math.abs(cart.amount),
    datePurchased: purchaseDayOf(cart.datePurchased),
  };
}

/**
 * Wählbare Empfänger im Edit-Modus des Zahlers (DIV-74): die aktuellen Empfänger plus der bisherige Empfänger, falls er
 * die Gruppe inzwischen verlassen hat. So bleibt die Ausgleichszahlung bearbeitbar; nur ein neu gewählter Empfänger
 * muss aktuell Mitglied sein (prüft das Backend).
 */
export function editRecipients(recipients: UserDto[], currentReceiver: UserDto): UserDto[] {
  return recipients.some(r => r.id === currentReceiver.id) ? recipients : [...recipients, currentReceiver];
}

function utcDay(value: Date | string): string {
  return new Date(value).toISOString().substring(0, 10);
}
