import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { amountFromInput, parseAmountText } from 'src/app/service/amount';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { UserDto } from 'src/app/model/user-dto';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import { SettlementPaymentDto } from '../model/settlement-payment-dto';
import { Cart } from '../model/cart';
import { purchaseDayOf } from '../filter-sheet/cart-filter-form';
import { SpendingsOverviewUserDto } from 'src/app/domains/overview/model/spendings-overview-user-dto';
import { isDeletedMarker, userDisplayName } from 'src/app/domains/overview/overview-display';

/**
 * Formularlogik der Ausgleichszahlung (DIV-70), verhaltensgleich aus der früheren SettlementPaymentPage übernommen.
 */

/**
 * Betrag: Pflichtfeld, größer als 0, ohne Vorzeichen (DIV-74; vorher waren 0 und negative Werte erlaubt); Punkt oder
 * Komma als Dezimaltrenner (DIV-77 Phase 9: Textfeld statt Zahlenfeld, damit „45.“ und „45,5“ ankommen). Die Richtung ergibt sich aus Zahler und Empfänger, nicht aus dem Vorzeichen. Mehr als zwei
 * Nachkommastellen lehnt das Formular nicht mehr ab, sondern das Sheet rundet sie beim Verlassen des Feldes und vor dem
 * Speichern auf Cent und schreibt den Betrag mit zwei Nachkommastellen ins Feld (DIV-77, D9: `formattedAmountInput`,
 * wie im Cart-Formular); wird dabei auf 0 gerundet, greift `min`. Das Backend lehnt weiterhin ab, was mit mehr als
 * zwei Nachkommastellen ankommt.
 */
export const AMOUNT_VALIDATORS: ValidatorFn[] = [
  Validators.required,
  // Textfeld (Nutzerentscheidung Phase 9): Punkt oder Komma, Trennzeichen am Ende erlaubt; ohne Vorzeichen
  Validators.pattern('(?:[0-9]+(?:[.,][0-9]*)?|[.,][0-9]+)'),
  positiveAmount,
];

/** Größer als 0 (Fehler `min` wie bisher `Validators.min(0.01)`), auch mit Komma: `Validators.min` liest „0,5“ als 0. */
export function positiveAmount(control: AbstractControl<number | string | null>): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === '') return null;
  const amount = typeof value === 'number' ? value : parseAmountText(value);
  return amount !== null && amount < 0.01 ? { min: { min: 0.01, actual: amount } } : null;
}

/** Formularwerte, wenn das Formular gültig ist; `datePurchased` als ISO-Tag `YYYY-MM-DD`. */
export interface SettlementFormValue {
  /** Zahl (Eingabe) oder Text mit zwei Nachkommastellen (nach dem Formatieren, Vorbelegung im Edit-Modus) */
  amount: number | string;
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
 * Request der Ausgleichszahlung. `datePurchased` ist der gewählte Tag als ISO-Tag `YYYY-MM-DD` (DIV-81). Ist die gewählte
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
    amount: amountFromInput(value.amount)!,
    groupId,
    member: received ? { id: options.receivedBy!.id, userName: options.receivedBy!.userName } : counterpart,
    datePurchased: value.datePurchased,
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
 * Wählbare Empfänger im Edit-Modus des Zahlers (DIV-74): die übergebene Auswahl plus der bisherige Empfänger, falls er
 * dort fehlt (Gruppe verlassen und Saldo 0, oder Salden nicht geladen). So bleibt die Ausgleichszahlung bearbeitbar.
 * Der ergänzte Empfänger kommt roh aus dem Cart und wird für die Anzeige aufgelöst (DIV-109: gelöscht → Label).
 */
export function editRecipients(recipients: SettlementParty[], currentReceiver: UserDto, deletedLabel: string): SettlementParty[] {
  return recipients.some(r => r.id === currentReceiver.id)
    ? recipients
    : [...recipients, displayParty(currentReceiver, deletedLabel)];
}

/**
 * Gegenpartei in der Auswahl (DIV-109). Aktuelle Mitglieder nur mit Id und Name; ehemalige Mitglieder und gelöschte
 * Nutzer mit `former` und ihrem offenen Saldo (`balance`, Gesamtsaldo der Übersicht), gelöschte zusätzlich `deleted`.
 * `userName` ist immer der Anzeigename (ohne Marker, gelöscht → Label).
 */
export interface SettlementParty extends UserDto {
  former?: boolean;
  deleted?: boolean;
  balance?: number;
}

/**
 * Anzeige einer Partei aus einem Cart (`userDto`): ein gelöschter Nutzer heißt dort `DELETED` und wird zum übersetzten
 * Label mit `deleted`, ein Marker `REMOVED` am Ende wird entfernt, sonst bleibt der Name (DIV-109).
 */
export function displayParty(user: UserDto, deletedLabel: string): SettlementParty {
  const deleted = isDeletedMarker(user.userName ?? '');
  return {
    id: user.id,
    userName: userDisplayName(user.userName ?? '', deletedLabel),
    ...(deleted ? { former: true, deleted: true } : {}),
  };
}

/**
 * Ehemalige Mitglieder und gelöschte Nutzer, mit denen eine Ausgleichszahlung erfasst werden kann (DIV-109): alle
 * Nutzer der Gesamtsalden (`balances`, Gruppe `groupId`), die weder ich noch Owner noch aktuelles Mitglied sind und deren
 * Saldo auf Cent gerundet ≠ 0 ist. „Aktuell“ entscheidet die Mitgliederliste, nicht der Marker im Namen; sie muss zu
 * `groupId` gehören, sonst (noch nicht geladen, andere Gruppe) gibt es keine ehemaligen Parteien, weil sonst jeder als
 * ehemalig gälte. Reihenfolge wie in `balances` (Historie).
 */
export function formerSettlementParties(balances: SpendingsOverviewUserDto[] | null, groupMembers: GroupMembers,
                                        groupId: number, userId: number | undefined,
                                        deletedLabel: string): SettlementParty[] {
  if (!balances || groupMembers.id !== groupId) return [];
  const current = new Set([groupMembers.ownerId, ...groupMembers.members.map(member => member.id)]);
  return balances
    .filter(user => user.userId !== userId && !current.has(user.userId) && Math.round(user.diff * 100) !== 0)
    .map(user => ({
      id: user.userId,
      userName: userDisplayName(user.userName, deletedLabel),
      former: true,
      deleted: isDeletedMarker(user.userName),
      balance: user.diff,
    }));
}

function utcDay(value: Date | string): string {
  return new Date(value).toISOString().substring(0, 10);
}
