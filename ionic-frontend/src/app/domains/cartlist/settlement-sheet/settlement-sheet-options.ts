import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { SettlementSheetComponent } from './settlement-sheet.component';

/** Zustand, den der Öffner erzeugt und mit dem Sheet teilt (die Schließ-Sperre liest dieselben Signale). */
export interface SettlementSheetState {
  /** Speichern läuft: Sheet ist gegen Schließen gesperrt. */
  saving: WritableSignal<boolean>;
}

/** Laufende Nummer für eindeutige Modal-IDs der Ausgleichszahlungs-Sheets. */
let sheetSeq = 0;

/**
 * Modal-Optionen der Ausgleichszahlung (DIV-70): inhaltshohes Bottom Sheet ohne Breakpoints und Griff, wie der
 * Ausgabenfilter (DIV-71). Ein Breakpoint-Sheet zog dort beim Bedienen der Felder mit. Gemeinsamer Basis-Style
 * `bottom-sheet`/`bottom-sheet-auto` (src/theme/bottom-sheet.scss).
 * Die Schließ-Sperre liest per `canDismiss` dasselbe Signal, das die Komponente während des Speicherns setzt (Muster
 * DIV-68): Backdrop, Hardware-Back und X schließen dann nicht. Ionic prüft `canDismiss` auch beim programmatischen
 * dismiss(); die Komponente setzt `saving` daher vor dem Schließen nach Erfolg zurück.
 * Eigene Modal-ID, mit der die Komponente gezielt sich selbst schließt (nicht den Datums-Picker darüber).
 */
export function settlementSheetModalOptions(state: SettlementSheetState): ModalOptions {
  const id = `settlement-sheet-${++sheetSeq}`;
  return {
    id,
    component: SettlementSheetComponent,
    componentProps: { modalId: id, saving: state.saving },
    cssClass: ['bottom-sheet', 'bottom-sheet-auto', 'settlement-sheet'],
    canDismiss: async () => !state.saving(),
  };
}
