import { Component, computed, Input, signal, WritableSignal } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { ShoppingSuggestionsDto } from '../model/shopping-suggestions-dto';
import { ShoppinglistService } from '../service/shoppinglist.service';

/**
 * Einkaufsvorschläge einer Liste als Bottom Sheet (DIV-69, ersetzt den Alert aus DIV-23): Mehrfachauswahl, Zähler,
 * „Alle auswählen“ und Hinzufügen der ausgewählten Namen in Vorschlagsreihenfolge. Den Ladezustand `adding` erzeugt der
 * Öffner (siehe suggestionsSheetModalOptions), damit die Schließ-Sperre des Modals dasselbe Signal liest. Bei Erfolg
 * schließt sich das Sheet, bei Fehler bleibt es mit der Auswahl offen (den Toast zeigt der Service). Der beim Öffnen
 * festgehaltene Kontext (Gruppe, Sitzung, Liste) gilt weiter: ist er weg, schließt Bestätigen ohne Request.
 *
 * Auswahl: das Signal `selected` ist die einzige Quelle. `ionChange` der Checkbox setzt den gemeldeten Zustand (kein
 * Toggle, doppelt gemeldet ist unschädlich), ein Klick auf den Namen toggelt. ion-item leitet nur Klicks auf seinen
 * Innenabstand an die Checkbox weiter, nicht Klicks auf das ion-label.
 */
@Component({
  selector: 'app-suggestions-sheet',
  templateUrl: './suggestions-sheet.component.html',
  styleUrls: ['./suggestions-sheet.component.scss'],
  standalone: false,
})
export class SuggestionsSheetComponent {

  @Input() listName!: string;
  @Input() suggestions!: ShoppingSuggestionsDto;
  @Input() groupId!: number;
  @Input() listId!: number;
  @Input() inContext!: () => boolean;
  @Input() adding!: WritableSignal<boolean>;
  /** ID des eigenen Modals (suggestionsSheetModalOptions): Schließen trifft nur dieses Sheet, nicht das oberste. */
  @Input() modalId!: string;

  selected = signal<ReadonlySet<string>>(new Set());
  selectedCount = computed(() => this.selected().size);
  allSelected = computed(() => this.names.length > 0 && this.selected().size === this.names.length);
  addLabelKey = computed(() => {
    const count = this.selectedCount();
    if (count === 0) return 'alerts.shoppinglist.suggestions.add_none';
    return count === 1 ? 'alerts.shoppinglist.suggestions.add_one' : 'alerts.shoppinglist.suggestions.add_other';
  });

  constructor(
    private shoppinglistService: ShoppinglistService,
    private modalCtrl: ModalController
  ) {}

  get names(): string[] {
    return this.suggestions.names;
  }

  get subtitleKey(): string {
    return this.suggestions.defaultSuggestions
      ? 'alerts.shoppinglist.suggestions.subtitle_defaults'
      : 'alerts.shoppinglist.suggestions.subtitle_history';
  }

  isSelected(name: string): boolean {
    return this.selected().has(name);
  }

  /** Aus dem ionChange der Checkbox: setzt den gemeldeten Zustand; während des Hinzufügens gesperrt. */
  setSelected(name: string, checked: boolean) {
    if (this.adding() || this.isSelected(name) === checked) return;
    this.selected.update(selected => {
      const next = new Set(selected);
      if (checked) next.add(name); else next.delete(name);
      return next;
    });
  }

  /** Klick auf den Namen; ein Klick, dessen Ziel in der Checkbox liegt, gehört der Checkbox (ionChange). */
  onNameClick(event: Event, name: string) {
    if ((event.target as HTMLElement | null)?.closest('ion-checkbox')) return;
    this.setSelected(name, !this.isSelected(name));
  }

  toggleAll() {
    if (this.adding()) return;
    this.selected.set(this.allSelected() ? new Set() : new Set(this.names));
  }

  async add() {
    if (this.adding() || this.selectedCount() === 0) return;
    if (!this.inContext()) {
      await this.dismiss();
      return;
    }
    // Vorschlagsreihenfolge, nicht Klickreihenfolge
    const names = this.names.filter(name => this.isSelected(name));
    this.adding.set(true);
    let ok = false;
    try {
      ok = await this.shoppinglistService.addItemsToShoppingList({ groupId: this.groupId, shoppingListId: this.listId, names });
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.adding.set(false);
    }
    if (ok) {
      await this.dismiss();
    }
  }

  close() {
    this.dismiss();
  }

  private dismiss() {
    return this.modalCtrl.dismiss(undefined, undefined, this.modalId);
  }
}
