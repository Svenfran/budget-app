import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { GROUP_GONE_ROLE } from '../group-sheet-roles';
import { MembersSheetComponent, MembersSheetView } from './members-sheet.component';

/** Rolle des X im Kopf: schließt das Sheet auch aus der Ansicht „Mitglied hinzufügen“. */
export const MEMBERS_SHEET_CLOSE_ROLE = 'close';

/** Zustand, den der Öffner erzeugt und mit dem Sheet teilt (die Schließ-Sperre liest dieselben Signale). */
export interface MembersSheetState {
  groupId: number;
  groupName: string;
  /** Request läuft (Hinzufügen, Entfernen, Admin wechseln): Sheet ist gegen Schließen gesperrt. */
  saving: WritableSignal<boolean>;
  /** Interner View-State (FA4): Mitgliederliste oder „Mitglied hinzufügen“ – im selben Sheet, kein zweites. */
  view: WritableSignal<MembersSheetView>;
}

/** Laufende Nummer für eindeutige Modal-IDs. */
let sheetSeq = 0;

/**
 * Modal-Optionen „Mitglieder verwalten“ (DIV-80): inhaltshohes Bottom Sheet ohne Breakpoints wie die übrigen Sheets.
 * `canDismiss` in dieser Reihenfolge (Ionic prüft es auch beim programmatischen dismiss(); nie wartend):
 * 1. Gruppe verschwunden (`GROUP_GONE_ROLE`): immer schließen – Vorrang vor Speichern und Ansicht (Codex F-002),
 * 2. während eines Requests: nicht schließen,
 * 3. Ansicht „Mitglied hinzufügen“: Hardware-Back/Backdrop führen zurück zur Liste (außer X = `MEMBERS_SHEET_CLOSE_ROLE`),
 * 4. sonst schließen.
 */
export function membersSheetModalOptions(state: MembersSheetState): ModalOptions {
  const id = `members-sheet-${++sheetSeq}`;
  return {
    id,
    component: MembersSheetComponent,
    componentProps: { modalId: id, groupId: state.groupId, groupName: state.groupName, saving: state.saving, view: state.view },
    cssClass: ['bottom-sheet', 'bottom-sheet-auto', 'members-sheet'],
    canDismiss: async (_data?: unknown, role?: string) => {
      if (role === GROUP_GONE_ROLE) return true;
      if (state.saving()) return false;
      if (state.view() === 'add' && role !== MEMBERS_SHEET_CLOSE_ROLE) {
        state.view.set('list');
        return false;
      }
      return true;
    },
  };
}
