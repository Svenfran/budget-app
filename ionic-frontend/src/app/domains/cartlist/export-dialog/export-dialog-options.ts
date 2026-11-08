import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { Group } from 'src/app/model/group';
import { ExportDialogComponent } from './export-dialog.component';

/**
 * Modal-Optionen des Export-Dialogs (DIV-68): unten angedockt, so hoch wie der Inhalt (Klasse `export-sheet` in
 * global.scss; Ionic-Sheets kennen nur feste Höhenanteile). Die Schließ-Sperre hängt per `canDismiss` an genau diesem
 * Modal und liest dasselbe Signal, das die Komponente während des Exports setzt: Backdrop und Hardware-Back schließen
 * den Dialog nicht, solange ein Export läuft. Ionic prüft `canDismiss` auch beim programmatischen dismiss(); die
 * Komponente setzt das Signal vor dem Schließen nach Erfolg zurück.
 */
export function exportDialogModalOptions(group: Group, exporting: WritableSignal<boolean>): ModalOptions {
  return {
    component: ExportDialogComponent,
    componentProps: { group, exporting },
    canDismiss: async () => !exporting(),
    cssClass: 'export-sheet',
  };
}
