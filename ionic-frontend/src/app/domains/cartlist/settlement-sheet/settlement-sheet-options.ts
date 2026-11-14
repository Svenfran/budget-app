import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { SETTLEMENT_SHEET_SAVED_ROLE, SettlementSheetComponent } from './settlement-sheet.component';

/** Zustand, den der Öffner erzeugt und mit dem Sheet teilt (die Schließ-Sperre liest dieselben Signale). */
export interface SettlementSheetState {
  /** Speichern läuft: Sheet ist gegen Schließen gesperrt. */
  saving: WritableSignal<boolean>;
  /** Eingaben wurden geändert (pflegt die Komponente): Schließen fragt nach. */
  dirty: WritableSignal<boolean>;
  /** Rückfrage „Eingaben verwerfen?“; true = verwerfen und schließen. */
  confirmDiscard: () => Promise<boolean>;
}

/** Laufende Nummer für eindeutige Modal-IDs der Ausgleichszahlungs-Sheets. */
let sheetSeq = 0;

/**
 * Modal-Optionen der Ausgleichszahlung (DIV-70): inhaltshohes Bottom Sheet ohne Breakpoints und Griff, wie der
 * Ausgabenfilter (DIV-71). Ein Breakpoint-Sheet zog dort beim Bedienen der Felder mit. Gemeinsamer Basis-Style
 * `bottom-sheet`/`bottom-sheet-auto` (src/theme/bottom-sheet.scss).
 * `canDismiss` liest dieselben Signale wie die Komponente (Muster DIV-68), Ionic prüft es auch beim programmatischen
 * dismiss():
 * - während des Speicherns: kein Schließen (X, Backdrop, Hardware-Back),
 * - nach erfolgreichem Speichern (Rolle `saved`): schließen, die Komponente setzt `saving` vorher zurück,
 * - unveränderte Eingaben: sofort schließen,
 * - geänderte Eingaben: Rückfrage „Eingaben verwerfen?“ (E1), geschlossen wird nur bei „Verwerfen“.
 * Eigene Modal-ID, mit der die Komponente gezielt sich selbst schließt (nicht den Datums-Picker darüber).
 */
export function settlementSheetModalOptions(state: SettlementSheetState): ModalOptions {
  const id = `settlement-sheet-${++sheetSeq}`;
  return {
    id,
    component: SettlementSheetComponent,
    componentProps: { modalId: id, saving: state.saving, dirty: state.dirty },
    cssClass: ['bottom-sheet', 'bottom-sheet-auto', 'settlement-sheet'],
    canDismiss: async (_data?: unknown, role?: string) => {
      if (state.saving()) return false;
      if (role === SETTLEMENT_SHEET_SAVED_ROLE || !state.dirty()) return true;
      return state.confirmDiscard();
    },
  };
}
