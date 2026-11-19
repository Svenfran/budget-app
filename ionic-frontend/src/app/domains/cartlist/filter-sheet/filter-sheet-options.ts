import { ModalOptions } from '@ionic/angular';
import { CartFilter } from '../model/cart-filter';
import { FilterSheetComponent } from './filter-sheet.component';

/** Laufende Nummer für eindeutige Modal-IDs der Filter-Sheets. */
let sheetSeq = 0;

/**
 * Modal-Optionen des Ausgabenfilters (DIV-71): unten angedockt und so hoch wie der Inhalt, ohne Breakpoints und Griff
 * (inhaltshohe Variante `bottom-sheet-auto` aus src/theme/bottom-sheet.scss, wie der Export-Dialog DIV-68). Ein
 * Breakpoint-Sheet zog beim Bedienen des Formulars mit (Wischen an Kopf, Footer oder Feldern); dieses Sheet bewegt
 * sich nicht. Den festen Footer und den scrollenden Mittelteil baut die Komponente selbst. Kein `canDismiss`: das
 * Sheet stellt keinen Request, Backdrop und Hardware-Back schließen jederzeit ohne Übernahme.
 * Eigene Modal-ID, mit der die Komponente gezielt sich selbst schließt (nicht ein darüberliegendes Picker-Modal oder
 * ein zweites, kurz danach geöffnetes Sheet).
 */
export function filterSheetModalOptions(cartFilter: CartFilter): ModalOptions {
  const id = `filter-sheet-${++sheetSeq}`;
  return {
    id,
    component: FilterSheetComponent,
    componentProps: { cartFilter, modalId: id },
    cssClass: ['bottom-sheet', 'bottom-sheet-auto', 'filter-sheet'],
  };
}
