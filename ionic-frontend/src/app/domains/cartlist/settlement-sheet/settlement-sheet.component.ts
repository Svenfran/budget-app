import { Component, computed, DestroyRef, Input, OnInit, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { take } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { GroupService } from 'src/app/service/group.service';
import { LanguageService } from 'src/app/service/language.service';
import { formatDisplayDate, isoDayOf, toIsoDay } from '../iso-day';
import { CartService } from '../service/cart.service';
import {
  AMOUNT_VALIDATORS, isDaySelectable, SettlementFormValue, settlementRecipients, toSettlementPaymentDto,
} from './settlement-form';

/** Rolle, mit der das Sheet nach erfolgreichem Speichern schließt. */
export const SETTLEMENT_SHEET_SAVED_ROLE = 'saved';

/** Was „OK“ im Picker vom ion-datetime braucht (Template-Referenz). */
export type SettlementDatePickerRef = Pick<IonDatetime, 'confirm' | 'value'>;

/**
 * Ausgleichszahlung als Bottom Sheet (DIV-70, ersetzt SettlementPaymentPage). Felder und Regeln wie bisher (Betrag,
 * Empfänger, Datum), Aufbau wie der Ausgabenfilter (DIV-71). Anders als früher speichert das Sheet selbst: bei Erfolg
 * schließt es, bei Fehler bleibt es mit den Eingaben offen (den Hinweis zeigt CartService). `saving` erzeugt der
 * Öffner, damit die Schließ-Sperre des Modals dasselbe Signal liest (settlementSheetModalOptions).
 *
 * Gruppe beim Öffnen festgehalten: Request, Empfänger-Laden und Datumsgrenzen beziehen sich auf genau diese Gruppe.
 * Datumsauswahl wie im Filter-Sheet: Inline-Modal mit ion-datetime, „OK“ bestätigt vor dem Lesen, `didDismiss` setzt
 * den Zustand nach Schließen von außen zurück.
 */
@Component({
  selector: 'app-settlement-sheet',
  templateUrl: './settlement-sheet.component.html',
  styleUrls: ['./settlement-sheet.component.scss'],
  standalone: false,
})
export class SettlementSheetComponent implements OnInit {

  /** ID des eigenen Modals (settlementSheetModalOptions): Schließen trifft nur dieses Sheet. */
  @Input() modalId!: string;
  @Input() saving!: WritableSignal<boolean>;
  /** Eingaben geändert: die Schließ-Sperre fragt dann vor dem Verwerfen nach (settlementSheetModalOptions). */
  @Input() dirty!: WritableSignal<boolean>;

  readonly group = this.groupService.activeGroup();
  readonly currentLang = this.langService.currentLang;
  readonly userId = signal<number | undefined>(undefined);
  readonly recipients = computed(() => settlementRecipients(this.groupService.groupMembers(), this.userId()));
  readonly openPicker = signal(false);
  /** Auswahl der Empfänger wurde geöffnet und wieder geschlossen (siehe showMemberError). */
  readonly memberSelectClosed = signal(false);

  /** Frühestens der Gründungstag der Gruppe, spätestens heute (wie bisher). */
  readonly minDate = toIsoDay(new Date(this.group.dateCreated));
  readonly maxDate = toIsoDay(new Date());

  form = new FormGroup({
    amount: new FormControl<number | null>(null, AMOUNT_VALIDATORS),
    memberId: new FormControl<number | null>(null, Validators.required),
    datePurchased: new FormControl<string | null>(this.maxDate, Validators.required),
  });

  constructor(
    private modalCtrl: ModalController,
    private groupService: GroupService,
    private cartService: CartService,
    private authService: AuthService,
    private langService: LanguageService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit() {
    this.authService.user.pipe(take(1)).subscribe(user => this.userId.set(user?.id));
    this.groupService.getGroupMembers(this.group.id);
    this.groupService.getGroupMembershipHistoryForGroupAndUser(this.group);

    // Über form.events statt valueChanges: das Datum wird per setValue() und danach markAsDirty() gesetzt; beim
    // Wert-Event ist das Formular noch pristine, erst das Pristine-Event zeigt die Änderung (Codex-Review 1, F-001).
    // Startwerte (Datum heute) sind vorher gesetzt und machen das Formular nicht dirty.
    this.dirty.set(this.form.dirty);
    this.form.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.dirty.set(this.form.dirty));
  }

  get amount() { return this.form.controls.amount; }
  get memberId() { return this.form.controls.memberId; }

  /** Feldfehler erst nach Bedienung zeigen, nicht beim Öffnen. */
  showError(control: FormControl): boolean {
    return control.invalid && (control.dirty || control.touched);
  }

  /**
   * Empfänger-Fehler erst, wenn die Auswahl ohne Wert geschlossen wurde. `touched` taugt hier nicht: ion-select meldet
   * `ionBlur` schon beim Öffnen der Auswahl (der Fokus wandert in den Dialog), der Hinweis stünde sonst sofort da.
   */
  showMemberError(): boolean {
    return this.memberId.invalid && (this.memberId.dirty || this.memberSelectClosed());
  }

  async save() {
    if (this.form.invalid || this.saving()) return;
    const dto = toSettlementPaymentDto(this.form.getRawValue() as SettlementFormValue, this.group.id, this.recipients());

    this.saving.set(true);
    this.form.disable({ emitEvent: false });
    let ok = false;
    try {
      ok = await this.cartService.addSettlementPayment(dto);
    } catch (err) {
      // Der Service weist laut Vertrag nie zurück; falls doch, bleibt das Sheet wie bei einem Fehler offen.
      console.error('Error saving settlement payment:', err);
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.saving.set(false);
      this.form.enable({ emitEvent: false });
    }
    if (ok) {
      await this.modalCtrl.dismiss(undefined, SETTLEMENT_SHEET_SAVED_ROLE, this.modalId);
    }
  }

  close() {
    this.modalCtrl.dismiss(undefined, 'cancel', this.modalId);
  }

  /** Enter im Betragsfeld schließt nur die Tastatur und speichert nicht. */
  blurInput(event: Event) {
    (event.target as HTMLElement | null)?.blur();
  }

  displayDate(): string {
    const value = this.form.controls.datePurchased.value;
    return value ? formatDisplayDate(value) : '';
  }

  /** Startwert des Pickers: der Formularwert (immer gesetzt, Pflichtfeld), sonst heute. */
  pickerValue(): string {
    return this.form.controls.datePurchased.value ?? this.maxDate;
  }

  /** Nur Tage in der eigenen Mitgliedschaft in dieser Gruppe (wie bisher; den Empfänger prüft das Backend). */
  isDateEnabled = (isoDay: string): boolean =>
    isDaySelectable(isoDay, this.groupService.groupMembershipHistory(), this.userId(), this.group.id);

  openDatePicker() {
    if (!this.saving()) this.openPicker.set(true);
  }

  /** „OK“: Auswahl zuerst bestätigen (sonst steht in `value` noch der alte Tag), dann übernehmen. */
  async pickDate(datetime: SettlementDatePickerRef) {
    await datetime.confirm(false);
    const value = datetime.value;
    if (typeof value === 'string' && value) {
      const control = this.form.controls.datePurchased;
      control.setValue(isoDayOf(value));
      control.markAsDirty();
    }
    this.openPicker.set(false);
  }

  /** „Abbrechen“ im Picker: Formular unverändert. */
  closeDatePicker() {
    this.openPicker.set(false);
  }

  /** Picker auf beliebigem Weg geschlossen (auch Backdrop/Hardware-Back): Zustand zurücksetzen, damit er wieder öffnet. */
  onPickerDismissed() {
    this.openPicker.set(false);
  }
}
