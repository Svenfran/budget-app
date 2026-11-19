import { Component, effect, Input, OnInit, signal, WritableSignal } from '@angular/core';
import { ModalController } from '@ionic/angular';
import { GroupService } from 'src/app/service/group.service';
import { GROUP_GONE_ROLE, GROUP_SHEET_SAVED_ROLE } from '../group-sheet-roles';
import { GroupOverview } from '../model/group-overview';
import { GROUP_NAME_MAX_LENGTH, groupForm, GroupFormGroup, toGroup } from './group-form';

export type GroupFormSheetMode = 'create' | 'edit';

/**
 * „Gruppe hinzufügen / bearbeiten“ als Bottom Sheet (DIV-80, ersetzt die Alerts mit Eingabefeld in der Gruppenübersicht).
 * Ein Formular für beide Modi (group-form.ts); das Sheet speichert selbst über GroupService: bei Erfolg schließt es, bei
 * Fehler bleibt es mit der Eingabe offen (den Hinweis zeigt GroupService als Toast). `saving` erzeugt der Öffner, damit
 * die Schließ-Sperre des Modals dasselbe Signal liest (groupFormSheetModalOptions).
 *
 * Bearbeiten: Verschwindet die Gruppe (gelöscht, Nutzer entfernt), schließt das Sheet mit GROUP_GONE_ROLE; ein danach
 * eintreffendes Ergebnis bewirkt nichts mehr.
 */
@Component({
  selector: 'app-group-form-sheet',
  templateUrl: './group-form-sheet.component.html',
  styleUrls: ['./group-form-sheet.component.scss'],
  standalone: false,
})
export class GroupFormSheetComponent implements OnInit {

  /** ID des eigenen Modals (groupFormSheetModalOptions): Schließen trifft nur dieses Sheet. */
  @Input() modalId!: string;
  @Input() mode: GroupFormSheetMode = 'create';
  /** Zu bearbeitende Gruppe (nur `edit`). */
  @Input() group?: GroupOverview;
  @Input() saving!: WritableSignal<boolean>;

  readonly maxLength = GROUP_NAME_MAX_LENGTH;
  form: GroupFormGroup = groupForm();
  /** Speichern mit ungültigem Formular versucht: ab dann zeigt das Feld seinen Fehler auch ohne Eingabe. */
  readonly submitted = signal(false);
  /** Sheet ist geschlossen oder schließt gerade (Gruppe verschwunden): keine weiteren Aktionen. */
  private closed = false;

  constructor(
    private modalCtrl: ModalController,
    private groupService: GroupService,
  ) {
    // Bearbeitete Gruppe verschwunden (Live-Event, eigener Austritt): Sheet schließen, Vorrang vor der Speicher-Sperre
    effect(() => {
      const list = this.groupService.groupOverviewList();
      if (this.mode !== 'edit' || !this.group || this.closed) return;
      if (!list.some(group => group.id === this.group!.id)) {
        this.closed = true;
        this.modalCtrl.dismiss(undefined, GROUP_GONE_ROLE, this.modalId);
      }
    });
  }

  ngOnInit() {
    this.form = groupForm(this.mode === 'edit' ? this.group?.name ?? '' : '');
  }

  get name() { return this.form.controls.name; }

  get isEdit(): boolean { return this.mode === 'edit'; }

  /**
   * Feldfehler erst nach einer Eingabe oder einem Speicherversuch zeigen – nicht schon, wenn das leere Feld den Fokus
   * verliert: Der Tipp aufs X löst zuerst den Blur aus, die eingeblendete Zeile ließe das unten verankerte Sheet nach
   * oben wachsen und das X unter dem Finger wegrutschen (der Klick ginge daneben).
   */
  showNameError(): boolean {
    return this.name.invalid && (this.name.dirty || this.submitted());
  }

  async save() {
    if (this.closed || this.saving()) return;
    if (this.form.invalid) {
      this.submitted.set(true);
      return;
    }
    const group = toGroup(this.form.getRawValue(), this.isEdit ? this.group : undefined);

    this.saving.set(true);
    this.form.disable({ emitEvent: false });
    let ok = false;
    try {
      ok = this.isEdit
        ? await this.groupService.updateGroup(group)
        : await this.groupService.addGroup(group);
    } catch (err) {
      // Der Service weist laut Vertrag nie zurück; falls doch, bleibt das Sheet wie bei einem Fehler offen.
      console.error('Error saving group:', err);
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.saving.set(false);
      this.form.enable({ emitEvent: false });
    }
    if (ok && !this.closed) {
      this.closed = true;
      await this.modalCtrl.dismiss(undefined, GROUP_SHEET_SAVED_ROLE, this.modalId);
    }
  }

  close() {
    this.modalCtrl.dismiss(undefined, 'cancel', this.modalId);
  }
}
