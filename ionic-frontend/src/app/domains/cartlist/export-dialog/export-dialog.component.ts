import { Component, computed, Input, signal, WritableSignal } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { Group } from 'src/app/model/group';
import { ExportFormat, ExportScope } from '../model/export-options';
import { CartService } from '../service/cart.service';

/**
 * Export-Dialog der Ausgaben-Seite (DIV-68) als Bottom Sheet: Umfang (aktuelle Gruppe / alle Gruppen) und Format
 * (XLSX / CSV-ZIP). Den Ladezustand `exporting` erzeugt der Öffner (siehe exportDialogModalOptions), damit die
 * Wegwisch-Sperre des Modals dasselbe Signal liest. Bei Erfolg schließt sich der Dialog, bei Fehler bleibt er offen.
 */
@Component({
  selector: 'app-export-dialog',
  templateUrl: './export-dialog.component.html',
  styleUrls: ['./export-dialog.component.scss'],
  standalone: false,
})
export class ExportDialogComponent {

  @Input() group!: Group;
  @Input() exporting!: WritableSignal<boolean>;

  scope = signal<ExportScope>('GROUP');
  format = signal<ExportFormat>('XLSX');

  xlsxDescriptionKey = computed(() => this.scope() === 'ALL'
    ? 'domains.spendings.export.format_xlsx_desc_all'
    : 'domains.spendings.export.format_xlsx_desc_group');
  csvDescriptionKey = computed(() => this.scope() === 'ALL'
    ? 'domains.spendings.export.format_csv_desc_all'
    : 'domains.spendings.export.format_csv_desc_group');

  constructor(
    private cartService: CartService,
    private modalCtrl: ModalController
  ) {}

  /** Aus dem ionChange der Radio-Gruppe und dem Klick auf die Zeile; während des Exports gesperrt. */
  onScopeChange(value: ExportScope) {
    if (!this.exporting()) this.scope.set(value);
  }

  onFormatChange(value: ExportFormat) {
    if (!this.exporting()) this.format.set(value);
  }

  async export() {
    if (this.exporting()) return;
    this.exporting.set(true);
    let ok = false;
    try {
      ok = await this.cartService.exportCarts({ scope: this.scope(), format: this.format() }, this.group);
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.exporting.set(false);
    }
    if (ok) {
      await this.modalCtrl.dismiss();
    }
  }

  cancel() {
    this.modalCtrl.dismiss();
  }
}
