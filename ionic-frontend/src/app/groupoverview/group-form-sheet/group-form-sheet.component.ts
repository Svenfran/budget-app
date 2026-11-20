import { Component, effect, Input, OnInit, signal, WritableSignal } from '@angular/core';
import { AlertController, ModalController } from '@ionic/angular';
import { TranslateService } from '@ngx-translate/core';
import { DEFAULT_CURRENCY, isSupportedCurrency } from 'src/app/constants/currency';
import { Group } from 'src/app/model/group';
import { currencySymbol, ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { GroupService, UpdateGroupResult } from 'src/app/service/group.service';
import { GROUP_GONE_ROLE, GROUP_SHEET_SAVED_ROLE } from '../group-sheet-roles';
import { GroupOverview } from '../model/group-overview';
import {
  currencyOptions, CurrencyOption, GROUP_NAME_MAX_LENGTH, groupForm, GroupFormGroup, needsCurrencyConfirmation, toGroup,
} from './group-form';

export type GroupFormSheetMode = 'create' | 'edit';

/** Prefix der Texte des Bestätigungsdialogs beim Währungswechsel (DIV-77). */
const CURRENCY_CONFIRM_KEY = 'groups.form_sheet.currency_confirm';
/** Beispielbetrag im Dialog: zeigt, dass nichts umgerechnet wird („100,00 € → 100,00 $“). */
const EXAMPLE_AMOUNT = 100;

/**
 * „Gruppe hinzufügen / bearbeiten“ als Bottom Sheet (DIV-80, ersetzt die Alerts mit Eingabefeld in der Gruppenübersicht).
 * Ein Formular für beide Modi (group-form.ts); das Sheet speichert selbst über GroupService: bei Erfolg schließt es, bei
 * Fehler bleibt es mit der Eingabe offen (den Hinweis zeigt GroupService als Toast). `saving` erzeugt der Öffner, damit
 * die Schließ-Sperre des Modals dasselbe Signal liest (groupFormSheetModalOptions).
 *
 * Bearbeiten: Verschwindet die Gruppe (gelöscht, Nutzer entfernt), schließt das Sheet mit GROUP_GONE_ROLE; ein danach
 * eintreffendes Ergebnis bewirkt nichts mehr, ein offener Bestätigungsdialog schließt mit.
 *
 * Währung (DIV-77): Ändert sich beim Bearbeiten die Währung einer Gruppe mit (möglichen) Buchungen, fragt ein Dialog
 * vorher nach; Abbrechen sendet nichts. Lehnt der Server trotzdem ab (409: Buchung inzwischen entstanden), fragt der
 * Dialog nachträglich und das Sheet sendet mit Bestätigung erneut.
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
  /** Aufrufende Seite beim Anlegen (GroupService.addGroup navigiert von „Keine Gruppe“ aus weiter). */
  @Input() callingPage?: string;

  readonly maxLength = GROUP_NAME_MAX_LENGTH;
  /**
   * Auswahl „USD – US-Dollar ($)“: Name per i18n im Template, Symbol wie in der Betragsanzeige; eine dieser App-Version
   * unbekannte Gruppenwährung steht ohne Namen am Ende und bleibt so erhalten.
   */
  currencyOptions: CurrencyOption[] = currencyOptions();
  form: GroupFormGroup = groupForm();
  /** Speichern mit ungültigem Formular versucht: ab dann zeigt das Feld seinen Fehler auch ohne Eingabe. */
  readonly submitted = signal(false);
  /** Sheet ist geschlossen oder schließt gerade (Gruppe verschwunden): keine weiteren Aktionen. */
  private closed = false;
  /** Bestätigungsdialog oder Speichern läuft: ein zweiter Tipp auf „Speichern“ bewirkt nichts. */
  private busy = false;
  private openAlert?: HTMLIonAlertElement;
  private readonly amountPipe = new ScientificCurrencyPipe();

  constructor(
    private modalCtrl: ModalController,
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private translate: TranslateService,
  ) {
    // Bearbeitete Gruppe verschwunden (Live-Event, eigener Austritt): Sheet schließen, Vorrang vor der Speicher-Sperre
    effect(() => {
      const list = this.groupService.groupOverviewList();
      if (this.mode !== 'edit' || !this.group || this.closed) return;
      if (!list.some(group => group.id === this.group!.id)) {
        this.closed = true;
        this.dismissOpenAlert();
        this.modalCtrl.dismiss(undefined, GROUP_GONE_ROLE, this.modalId);
      }
    });
  }

  ngOnInit() {
    this.form = this.mode === 'edit'
      ? groupForm(this.group?.name ?? '', this.group?.currencyCode)
      : groupForm('');
    this.currencyOptions = currencyOptions(this.form.controls.currencyCode.value);
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
    if (this.closed || this.saving() || this.busy) return;
    if (this.form.invalid) {
      this.submitted.set(true);
      return;
    }
    const value = this.form.getRawValue();
    const group = toGroup(value, this.isEdit ? this.group : undefined);

    this.busy = true;
    try {
      let confirmed = false;
      if (this.isEdit && needsCurrencyConfirmation(this.group, value)) {
        if (!await this.confirmCurrencyChange(value.currencyCode)) return;
        confirmed = true;
      }

      let result = await this.submit(group, confirmed);
      // Buchung inzwischen entstanden (409): nachträglich fragen und mit Bestätigung erneut senden
      if (result === 'confirmation-required' && !confirmed && !this.closed) {
        if (!await this.confirmCurrencyChange(value.currencyCode)) return;
        result = await this.submit(group, true);
      }

      if (result === 'ok' && !this.closed) {
        this.closed = true;
        await this.modalCtrl.dismiss(undefined, GROUP_SHEET_SAVED_ROLE, this.modalId);
      }
    } finally {
      this.busy = false;
    }
  }

  close() {
    this.modalCtrl.dismiss(undefined, 'cancel', this.modalId);
  }

  /**
   * Ein Request mit Lade- und Sperrzustand; Fehler sind `'error'` (der Service weist laut Vertrag nie zurück). Bei
   * Erfolg bleiben die Felder gesperrt, bis das Sheet geschlossen ist: kurz wieder aktive Felder flackern sonst sichtbar.
   */
  private async submit(group: Group, confirmCurrencyChange: boolean): Promise<UpdateGroupResult> {
    if (this.closed) return 'error';
    this.saving.set(true);
    this.form.disable({ emitEvent: false });
    let result: UpdateGroupResult = 'error';
    try {
      if (!this.isEdit) {
        result = await this.groupService.addGroup(group, this.callingPage) ? 'ok' : 'error';
      } else {
        result = await this.groupService.updateGroup(confirmCurrencyChange ? { ...group, confirmCurrencyChange: true } : group);
      }
    } catch (err) {
      // Der Service weist laut Vertrag nie zurück; falls doch, bleibt das Sheet wie bei einem Fehler offen.
      console.error('Error saving group:', err);
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.saving.set(false);
      if (result !== 'ok') this.form.enable({ emitEvent: false });
    }
    return result;
  }

  /**
   * Bestätigungsdialog (AC6/AC7): erklärt, dass alle bisherigen Beträge künftig in der neuen Währung gelten und nichts
   * umgerechnet wird; true nur bei ausdrücklicher Bestätigung. Der Dialog gehört zum Sheet (Muster MembersSheet): Ist
   * das Sheet beim Fertigstellen schon zu, erscheint er nicht; schließt das Sheet, schließt er mit.
   */
  private async confirmCurrencyChange(newCurrency: string): Promise<boolean> {
    const oldCurrency = this.group?.currencyCode ?? DEFAULT_CURRENCY;
    const newName = isSupportedCurrency(newCurrency) ? this.translate.instant(`currency.name.${newCurrency}`) : newCurrency;
    const alert = await this.alertCtrl.create({
      header: this.translate.instant(`${CURRENCY_CONFIRM_KEY}.header`),
      message: this.translate.instant(`${CURRENCY_CONFIRM_KEY}.message`, {
        currency: `${newName} (${currencySymbol(newCurrency)})`,
        before: this.amountPipe.transform(EXAMPLE_AMOUNT, oldCurrency),
        after: this.amountPipe.transform(EXAMPLE_AMOUNT, newCurrency),
      }),
      buttons: [
        { text: this.translate.instant(`${CURRENCY_CONFIRM_KEY}.cancel`), role: 'cancel' },
        { text: this.translate.instant(`${CURRENCY_CONFIRM_KEY}.ok`), role: 'confirm' },
      ],
    });
    if (this.closed) {
      // Ionic hängt den Alert schon beim Erzeugen an; nie präsentiert hilft nur remove()
      alert.remove();
      return false;
    }
    this.openAlert = alert;
    // Fokus lösen: Ionic gibt ihn beim Schließen des Dialogs zurück, ein fokussiertes Namensfeld öffnete dann kurz die
    // Tastatur, bevor das Sheet schließt (Flackern)
    (document.activeElement as HTMLElement | null)?.blur();
    try {
      await alert.present();
      const { role } = await alert.onDidDismiss();
      return role === 'confirm' && !this.closed;
    } finally {
      if (this.openAlert === alert) this.openAlert = undefined;
    }
  }

  private dismissOpenAlert() {
    this.openAlert?.dismiss().catch(() => undefined);
    this.openAlert = undefined;
  }
}
