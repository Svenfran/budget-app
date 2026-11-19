import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { GroupOverview } from '../model/group-overview';
import { GROUP_GONE_ROLE } from '../group-sheet-roles';
import { GroupFormSheetComponent, GroupFormSheetMode } from './group-form-sheet.component';

/** Zustand, den der Öffner erzeugt und mit dem Sheet teilt (die Schließ-Sperre liest dasselbe Signal). */
export interface GroupFormSheetState {
  mode: GroupFormSheetMode;
  /** Zu bearbeitende Gruppe (nur `edit`). */
  group?: GroupOverview;
  /** Speichern läuft: Sheet ist gegen Schließen gesperrt. */
  saving: WritableSignal<boolean>;
}

/** Laufende Nummer für eindeutige Modal-IDs. */
let sheetSeq = 0;

/**
 * Modal-Optionen „Gruppe hinzufügen / bearbeiten“ (DIV-80): inhaltshohes Bottom Sheet ohne Breakpoints und Griff wie die
 * Ausgleichszahlung (gemeinsamer Basis-Style `bottom-sheet`/`bottom-sheet-auto`, src/theme/bottom-sheet.scss).
 * `canDismiss` (Ionic prüft es auch beim programmatischen dismiss()):
 * - Gruppe verschwunden (`GROUP_GONE_ROLE`): immer schließen, auch während des Speicherns,
 * - während des Speicherns: kein Schließen (X, Abbrechen, Backdrop, Hardware-Back),
 * - sonst schließen (ohne Rückfrage, das Formular hat nur ein Feld).
 */
export function groupFormSheetModalOptions(state: GroupFormSheetState): ModalOptions {
  const id = `group-form-sheet-${++sheetSeq}`;
  return {
    id,
    component: GroupFormSheetComponent,
    componentProps: { modalId: id, mode: state.mode, group: state.group, saving: state.saving },
    cssClass: ['bottom-sheet', 'bottom-sheet-auto', 'group-form-sheet'],
    canDismiss: async (_data?: unknown, role?: string) => role === GROUP_GONE_ROLE || !state.saving(),
  };
}
