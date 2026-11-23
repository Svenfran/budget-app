import { Component, computed, DestroyRef, Input, OnInit, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { take } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { GroupService } from 'src/app/service/group.service';
import { LanguageService } from 'src/app/service/language.service';
import { amountInputText, formattedAmountInput } from 'src/app/service/amount';
import { currencySymbol } from 'src/app/pipe/scientific.pipe';
import { DEFAULT_CURRENCY } from 'src/app/constants/currency';
import { formatDisplayDate, isoDayOf, toIsoDay } from '../iso-day';
import { CartService } from '../service/cart.service';
import {
  AMOUNT_VALIDATORS, editRecipients, isDaySelectable, SettlementDirection, SettlementEditData, SettlementFormValue,
  settlementRecipients, toSettlementPaymentDto,
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
  /**
   * Bestehende Ausgleichszahlung (DIV-74): mit Wert öffnet das Sheet im Edit-Modus, vorbelegt aus dem Cart-Paar. Die
   * Parteien kommen von hier, nicht aus der aktuellen Mitgliederliste – so bleibt die Zahlung auch bearbeitbar, wenn
   * die Gegenpartei die Gruppe verlassen hat. Der Empfänger ist nur für den Zahler änderbar.
   */
  @Input() settlement?: SettlementEditData;

  readonly group = this.groupService.activeGroup();
  /**
   * Währungssymbol im Betrags-Label (DIV-77): die Gruppe bleibt die beim Öffnen festgehaltene, ihre Währung aber die
   * aktuelle aus der Gruppenliste (der Live-Handler aktualisiert sie für alle Gruppen) – ändert der Admin sie bei
   * offenem Sheet, gilt die Zahlung in der neuen Währung (Codex F-001).
   */
  readonly currencySymbol = computed(() => {
    const current = this.groupService.groupsSideNav().find(group => group.id === this.group.id);
    return currencySymbol(current?.currencyCode ?? this.group.currencyCode ?? DEFAULT_CURRENCY);
  });
  readonly currentLang = this.langService.currentLang;
  readonly userId = signal<number | undefined>(undefined);
  readonly userName = signal('');
  /**
   * Richtung (DIV-74): „Ich habe gezahlt an …“ oder „Ich habe erhalten von …“. Bewusst kein Formularfeld, damit die
   * bestehenden Formularwerte unverändert bleiben; ein Wechsel markiert das Formular als geändert. Im Edit-Modus fest
   * aus der Rolle (der Zahler ist nicht änderbar).
   */
  readonly direction = signal<SettlementDirection>('paid');
  readonly recipients = computed(() => settlementRecipients(this.groupService.groupMembers(), this.userId()));
  /** Auswahlliste im Edit-Modus des Zahlers: aktuelle Empfänger plus der bisherige (ggf. ausgetretene) Empfänger. */
  readonly selectableRecipients = computed(() => this.settlement
    ? editRecipients(this.recipients(), this.settlement.receiver)
    : this.recipients());
  /** Antwort noch gültig? Im Edit-Modus beim Öffnen festgehalten (aktive Gruppe und Anmelde-Sitzung). */
  private isCurrent: () => boolean = () => true;
  readonly openPicker = signal(false);
  /** Auswahl der Empfänger wurde geöffnet und wieder geschlossen (siehe showMemberError). */
  readonly memberSelectClosed = signal(false);

  /** Frühestens der Gründungstag der Gruppe, spätestens heute (wie bisher). */
  readonly minDate = toIsoDay(new Date(this.group.dateCreated));
  readonly maxDate = toIsoDay(new Date());

  form = new FormGroup({
    amount: new FormControl<number | string | null>(null, AMOUNT_VALIDATORS),
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
    this.authService.user.pipe(take(1)).subscribe(user => {
      this.userId.set(user?.id);
      this.userName.set(user?.name ?? '');
    });
    this.groupService.getGroupMembers(this.group.id);
    this.groupService.getGroupMembershipHistoryForGroupAndUser(this.group);

    if (this.settlement) {
      // Vorbelegung vor dem Abonnieren der Formular-Events: macht das Formular nicht dirty
      this.form.setValue({
        amount: amountInputText(this.settlement.amount),
        memberId: this.settlement.receiver.id,
        datePurchased: this.settlement.datePurchased,
      });
      if (this.settlement.role === 'receiver') this.memberId.disable();
      this.direction.set(this.settlement.role === 'payer' ? 'paid' : 'received');
      const groupId = this.group.id;
      const sameSession = this.groupService.sessionGuard();
      this.isCurrent = () => this.groupService.activeGroupId() === groupId && sameSession();
    }

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

  /** Formular und „Speichern“ anzeigen: im Edit-Modus immer, sonst nur mit auswählbarem Empfänger (wie bisher). */
  showForm(): boolean {
    return !!this.settlement || this.recipients().length > 0;
  }

  /** Empfänger-Auswahl nur bei Neuanlage und für den Zahler; der Empfänger sieht den Zahler als festen Text. */
  canSelectReceiver(): boolean {
    return this.settlement?.role !== 'receiver';
  }

  /** Richtungsumschalter (nur beim Anlegen): ein Wechsel gilt als Eingabe (Rückfrage beim Verwerfen). */
  changeDirection(value: unknown) {
    if (this.settlement || (value !== 'paid' && value !== 'received') || value === this.direction()) return;
    this.direction.set(value);
    this.form.markAsDirty();
  }

  /** Platzhalter der Gegenpartei-Auswahl je Richtung. */
  memberPlaceholderKey(): string {
    return this.direction() === 'received'
      ? 'domains.spendings.overlay.settlementpayment.placeholder_member_from'
      : 'domains.spendings.overlay.settlementpayment.placeholder_member';
  }

  /**
   * Betrag auf Cent runden und mit zwei Nachkommastellen sichtbar ins Feld schreiben (DIV-77, D9; einheitlich mit dem
   * Cart-Formular): beim Verlassen des Feldes und vor dem Speichern. Ungültige Eingaben bleiben unverändert
   * (Validatoren melden sie). Programmatisch gesetzt, macht das Sheet also nicht „geändert“.
   */
  roundAmount() {
    if (this.saving()) return;
    const formatted = formattedAmountInput(this.amount.value);
    if (formatted !== null && formatted !== this.amount.value) this.amount.setValue(formatted);
  }

  async save() {
    this.roundAmount();
    if (this.form.invalid || this.saving()) return;
    const recipients = this.settlement ? [...this.selectableRecipients(), this.settlement.receiver] : this.recipients();
    const receivedBy = this.direction() === 'received' ? { id: this.userId()!, userName: this.userName() } : undefined;
    const dto = toSettlementPaymentDto(this.form.getRawValue() as SettlementFormValue, this.group.id, recipients,
      { settlementId: this.settlement?.id, receivedBy });

    this.saving.set(true);
    this.form.disable({ emitEvent: false });
    let ok = false;
    try {
      ok = this.settlement
        ? await this.cartService.updateSettlementPayment(dto, this.group, this.isCurrent, this.settlement)
        : await this.cartService.addSettlementPayment(dto);
    } catch (err) {
      // Der Service weist laut Vertrag nie zurück; falls doch, bleibt das Sheet wie bei einem Fehler offen.
      console.error('Error saving settlement payment:', err);
    } finally {
      // zuerst zurücksetzen: canDismiss liest dasselbe Signal und würde das Schließen sonst blockieren
      this.saving.set(false);
      this.form.enable({ emitEvent: false });
      if (this.settlement?.role === 'receiver') this.memberId.disable({ emitEvent: false });
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
