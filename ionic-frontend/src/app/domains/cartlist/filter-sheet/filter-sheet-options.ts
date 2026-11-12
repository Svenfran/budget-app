import { ModalOptions } from '@ionic/angular';
import { CartFilter } from '../model/cart-filter';
import { FilterSheetComponent } from './filter-sheet.component';

/** Laufende Nummer für eindeutige Modal-IDs der Filter-Sheets. */
let sheetSeq = 0;

/**
 * Modal-Optionen des Ausgabenfilters (DIV-71): Ionic-Sheet mit Breakpoints (Start bei 75 %, ganz hoch oder wegwischen)
 * und Griff; `expandToScroll: false` lässt bei jedem Breakpoint nur die Felder scrollen und hält den Footer mit „Reset“
 * und „Filter anwenden“ sichtbar. Gemeinsamer Basis-Style `bottom-sheet` (src/theme/bottom-sheet.scss). Kein
 * `canDismiss`: das Sheet stellt keinen Request, Wegwischen/Backdrop/Hardware-Back schließen jederzeit ohne Übernahme.
 * Eigene Modal-ID, mit der die Komponente gezielt sich selbst schließt (nicht ein darüberliegendes Picker-Modal oder
 * ein zweites, kurz danach geöffnetes Sheet).
 */
export function filterSheetModalOptions(cartFilter: CartFilter): ModalOptions {
  const id = `filter-sheet-${++sheetSeq}`;
  return {
    id,
    component: FilterSheetComponent,
    componentProps: { cartFilter, modalId: id },
    breakpoints: [0, 0.75, 1],
    initialBreakpoint: 0.75,
    expandToScroll: false,
    handle: true,
    cssClass: ['bottom-sheet', 'filter-sheet'],
  };
}
