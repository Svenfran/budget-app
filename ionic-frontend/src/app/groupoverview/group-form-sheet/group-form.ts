import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { CurrencyCode, DEFAULT_CURRENCY, isSupportedCurrency } from 'src/app/constants/currency';
import { INIT_NUMBERS } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';
import { GroupOverview } from '../model/group-overview';

/*
 * Formular „Gruppe hinzufügen / bearbeiten“ (DIV-80) als pure Funktionen. Regeln wie bisher im Alert: Name getrimmt,
 * nicht leer, höchstens INIT_NUMBERS.MAX_LENGTH Zeichen. DIV-77: Gruppenwährung (neue Gruppe EUR, beim Bearbeiten die
 * Währung der Gruppe).
 */

export const GROUP_NAME_MAX_LENGTH = INIT_NUMBERS.MAX_LENGTH;

export interface GroupFormValue {
  name: string;
  currencyCode: CurrencyCode;
}

export type GroupFormGroup = FormGroup<{ name: FormControl<string>; currencyCode: FormControl<CurrencyCode> }>;

/** Nur Leerzeichen zählen als leer (bisher: `trim() === ""` wurde verworfen). */
export function notBlank(control: AbstractControl<string | null>): ValidationErrors | null {
  return (control.value ?? '').trim() === '' ? { required: true } : null;
}

/** `initialCurrency`: Währung der bearbeiteten Gruppe; fehlt sie oder ist sie unbekannt, EUR. */
export function groupForm(initialName = '', initialCurrency?: string): GroupFormGroup {
  return new FormGroup({
    name: new FormControl(initialName, {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(GROUP_NAME_MAX_LENGTH)],
    }),
    currencyCode: new FormControl<CurrencyCode>(
      isSupportedCurrency(initialCurrency) ? initialCurrency : DEFAULT_CURRENCY,
      { nonNullable: true, validators: [Validators.required] },
    ),
  });
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
