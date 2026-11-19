import { AbstractControl, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { INIT_NUMBERS } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';

/*
 * Formular „Gruppe hinzufügen / bearbeiten“ (DIV-80) als pure Funktionen. Regeln wie bisher im Alert: Name getrimmt,
 * nicht leer, höchstens INIT_NUMBERS.MAX_LENGTH Zeichen. Weitere Felder (Folgestory: Währung) kommen als zusätzliches
 * Control in `groupForm` und als Feld in `toGroup` dazu.
 */

export const GROUP_NAME_MAX_LENGTH = INIT_NUMBERS.MAX_LENGTH;

export interface GroupFormValue {
  name: string;
}

export type GroupFormGroup = FormGroup<{ name: FormControl<string> }>;

/** Nur Leerzeichen zählen als leer (bisher: `trim() === ""` wurde verworfen). */
export function notBlank(control: AbstractControl<string | null>): ValidationErrors | null {
  return (control.value ?? '').trim() === '' ? { required: true } : null;
}

export function groupForm(initialName = ''): GroupFormGroup {
  return new FormGroup({
    name: new FormControl(initialName, {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(GROUP_NAME_MAX_LENGTH)],
    }),
  });
}

/**
 * Request-Objekt aus den Formularwerten: Name getrimmt; beim Bearbeiten ID und Gründungsdatum der bestehenden Gruppe.
 * Die ID einer neuen Gruppe vergibt das Backend (wie bisher wird ein Platzhalter mitgeschickt).
 */
export function toGroup(value: GroupFormValue, existing?: Pick<Group, 'id' | 'dateCreated'>): Group {
  const now = new Date();
  return {
    id: existing?.id ?? now.getTime(),
    name: value.name.trim(),
    dateCreated: existing?.dateCreated ?? now,
  };
}
