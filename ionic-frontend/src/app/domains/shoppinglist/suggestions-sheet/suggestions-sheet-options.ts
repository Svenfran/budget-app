import { WritableSignal } from '@angular/core';
import { ModalOptions } from '@ionic/angular';
import { ShoppingSuggestionsDto } from '../model/shopping-suggestions-dto';
import { SuggestionsSheetComponent } from './suggestions-sheet.component';

/** Daten des Vorschlags-Sheets, beim Öffnen festgehalten (DIV-69). */
export interface SuggestionsSheetProps {
  listName: string;
  suggestions: ShoppingSuggestionsDto;
  groupId: number;
  listId: number;
  /** Kontext beim Öffnen (Gruppe, Sitzung, Liste), siehe ShoppinglistService.captureSuggestionContext */
  inContext: () => boolean;
}

/**
 * Modal-Optionen des Vorschlags-Sheets (DIV-69): Ionic-Sheet mit Breakpoints (Start bei 75 %, ganz hoch oder
 * wegwischen) und Griff; `expandToScroll: false` lässt bei jedem Breakpoint nur die Liste scrollen und hält den Footer
 * mit dem Hinzufügen-Button sichtbar. Gemeinsamer Basis-Style `bottom-sheet` (src/theme/bottom-sheet.scss). Die
 * Schließ-Sperre liest per `canDismiss` dasselbe Signal, das die Komponente während des Hinzufügens setzt: Wegwischen,
 * Backdrop und Hardware-Back schließen dann nicht.
 */
export function suggestionsSheetModalOptions(props: SuggestionsSheetProps, adding: WritableSignal<boolean>): ModalOptions {
  return {
    component: SuggestionsSheetComponent,
    componentProps: { ...props, adding },
    breakpoints: [0, 0.75, 1],
    initialBreakpoint: 0.75,
    expandToScroll: false,
    handle: true,
    canDismiss: async () => !adding(),
    cssClass: 'bottom-sheet',
  };
}
