import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { DEFAULT_CURRENCY, isSupportedCurrency, SUPPORTED_CURRENCIES } from 'src/app/constants/currency';
import { INIT_NUMBERS } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';
import { currencySymbol } from 'src/app/pipe/scientific.pipe';
import { GroupOverview } from '../model/group-overview';

/*
 * Formular „Gruppe hinzufügen / bearbeiten“ (DIV-80) als pure Funktionen. Regeln wie bisher im Alert: Name getrimmt,
 * nicht leer, höchstens INIT_NUMBERS.MAX_LENGTH Zeichen. DIV-77: Gruppenwährung (neue Gruppe EUR, beim Bearbeiten die
 * Währung der Gruppe).
 */

export const GROUP_NAME_MAX_LENGTH = INIT_NUMBERS.MAX_LENGTH;

/**
 * `currencyCode` ist bewusst `string`, nicht `CurrencyCode`: Eine Gruppe kann eine Währung haben, die diese App-Version
 * noch nicht kennt (neuere Version hat sie eingeführt) – sie bleibt beim Bearbeiten erhalten.
 */
export interface GroupFormValue {
  name: string;
  currencyCode: string;
}

export type GroupFormGroup = FormGroup<{ name: FormControl<string>; currencyCode: FormControl<string> }>;

/** Eintrag der Währungsauswahl; `known`: in dieser App-Version unterstützt (nur dann gibt es einen übersetzten Namen). */
export interface CurrencyOption {
  code: string;
  symbol: string;
  known: boolean;
}

/** ISO-4217-Form; nur so ein Code wird übernommen (Intl.NumberFormat wirft bei anderen). */
const ISO_CODE = /^[A-Z]{3}$/;

/** Nur Leerzeichen zählen als leer (bisher: `trim() === ""` wurde verworfen). */
export function notBlank(control: AbstractControl<string | null>): ValidationErrors | null {
  return (control.value ?? '').trim() === '' ? { required: true } : null;
}

/**
 * `initialCurrency`: Währung der bearbeiteten Gruppe. Fehlt sie (Daten von vor DIV-77) oder ist sie kein ISO-Code, EUR.
 * Ein gültiger, dieser App-Version aber unbekannter Code bleibt erhalten – sonst stellte eine ältere App die Gruppe beim
 * Umbenennen still auf EUR um.
 */
export function groupForm(initialName = '', initialCurrency?: string): GroupFormGroup {
  return new FormGroup({
    name: new FormControl(initialName, {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(GROUP_NAME_MAX_LENGTH)],
    }),
    currencyCode: new FormControl<string>(
      initialCurrency && ISO_CODE.test(initialCurrency) ? initialCurrency : DEFAULT_CURRENCY,
      { nonNullable: true, validators: [Validators.required] },
    ),
  });
}

/** Auswahl: alle unterstützten Währungen, dazu die aktuelle, falls diese App-Version sie nicht kennt (am Ende). */
export function currencyOptions(current?: string): CurrencyOption[] {
  const codes: string[] = [...SUPPORTED_CURRENCIES];
  if (current && ISO_CODE.test(current) && !codes.includes(current)) codes.push(current);
  return codes.map(code => ({ code, symbol: currencySymbol(code), known: isSupportedCurrency(code) }));
}

/**
 * Request-Objekt aus den Formularwerten: Name getrimmt, Währung; beim Bearbeiten ID und Gründungsdatum der bestehenden
 * Gruppe. Die ID einer neuen Gruppe vergibt das Backend (wie bisher wird ein Platzhalter mitgeschickt).
 */
export function toGroup(value: GroupFormValue, existing?: Pick<Group, 'id' | 'dateCreated'>): Group {
  const now = new Date();
  return {
    id: existing?.id ?? now.getTime(),
    name: value.name.trim(),
    dateCreated: existing?.dateCreated ?? now,
    currencyCode: value.currencyCode,
  };
}

/**
 * Braucht das Speichern die ausdrückliche Bestätigung des Währungswechsels (DIV-77, AC6/AC7)? Nur beim Bearbeiten, wenn
 * sich die Währung ändert und die Gruppe Buchungen hat – ein fehlendes `hasBookings` (Gruppe per Live-Event in die
 * Übersicht gekommen) gilt vorsorglich als „Buchungen möglich“. Der Server verlangt die Bestätigung ohnehin (409).
 */
export function needsCurrencyConfirmation(
  group: Pick<GroupOverview, 'currencyCode' | 'hasBookings'> | undefined, value: Pick<GroupFormValue, 'currencyCode'>
): boolean {
  if (!group) return false;
  return value.currencyCode !== (group.currencyCode ?? DEFAULT_CURRENCY) && group.hasBookings !== false;
}
