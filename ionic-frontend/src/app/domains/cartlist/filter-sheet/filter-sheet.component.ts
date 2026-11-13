import { Component, Input, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { IonDatetime, ModalController } from '@ionic/angular';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { LanguageService } from 'src/app/service/language.service';
import { CartFilter } from '../model/cart-filter';
import {
  CartFilterFormValue, dateRangeValidator, formatDisplayDate, isoDayOf, toCartFilter, toFormValue, toIsoDay,
} from './cart-filter-form';

/** Rolle, mit der das Filter-Sheet bei „Filter anwenden“ schließt; nur dann übernimmt die Ausgaben-Seite den Filter. */
export const FILTER_SHEET_APPLY_ROLE = 'apply';

export type DateField = 'startDate' | 'endDate';

/** Was „Speichern“ im Picker vom ion-datetime braucht (Template-Referenz). */
export type DatePickerRef = Pick<IonDatetime, 'confirm' | 'value'>;

/**
 * Ausgabenfilter als Bottom Sheet (DIV-71, ersetzt FilterModalPage). Das Formular ist der Bearbeitungszustand und
 * startet als Kopie des angewendeten Filters; übernommen wird nur über „Filter anwenden“ (Rolle `apply`). „Reset“ leert
 * nur die Felder, X/Backdrop/Hardware-Back schließen ohne Übernahme.
 *
 * Datumsauswahl: je Feld ein Inline-Modal mit ion-datetime. Mit eigenen Buttons übernimmt ion-datetime einen
 * angeklickten Tag erst per `confirm()` in `value`, daher bestätigt „Speichern“ zuerst und liest danach. Ionic setzt
 * `isOpen` beim Schließen von außen (Backdrop, Hardware-Back) nicht zurück, daher setzt `didDismiss` den Zustand zurück.
 * Der Picker-Inhalt wird bei jedem Öffnen neu erzeugt und zeigt so immer den Formularwert, nie eine verworfene Auswahl.
 */
@Component({
  selector: 'app-filter-sheet',
  templateUrl: './filter-sheet.component.html',
  styleUrls: ['./filter-sheet.component.scss'],
  standalone: false,
})
export class FilterSheetComponent implements OnInit {

  @Input() cartFilter: CartFilter = {};
  /** ID des eigenen Modals (filterSheetModalOptions): Schließen trifft nur dieses Sheet. */
  @Input() modalId!: string;

  readonly dateFields: { field: DateField; labelKey: string }[] = [
    { field: 'startDate', labelKey: 'domains.spendings.overlay.filter.date_from' },
    { field: 'endDate', labelKey: 'domains.spendings.overlay.filter.date_to' },
  ];

  categoryList = this.categoryService.categories;
  members = this.groupService.groupMembersWithOwner;
  currentLang = this.langService.currentLang;
  openPicker = signal<DateField | null>(null);

  form = new FormGroup({
    title: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
    categories: new FormControl<string[] | null>(null),
    members: new FormControl<string[] | null>(null),
    startDate: new FormControl<string | null>(null),
    endDate: new FormControl<string | null>(null),
  }, { validators: dateRangeValidator });

  constructor(
    private modalCtrl: ModalController,
    private categoryService: CategoryService,
    private groupService: GroupService,
    private langService: LanguageService
  ) {}

  ngOnInit() {
    this.form.setValue(toFormValue(this.cartFilter));
    // Auswahllisten aktuell halten (Kategorien sind nicht live synchronisiert), wie bisher beim Öffnen
    const group = this.groupService.activeGroup();
    this.groupService.getGroupMembers(group.id);
    this.categoryService.getCategoriesByGroup(group);
  }

  apply() {
    if (this.form.invalid) return;
    const value: CartFilterFormValue = this.form.getRawValue();
    this.modalCtrl.dismiss(toCartFilter(value), FILTER_SHEET_APPLY_ROLE, this.modalId);
  }

  reset() {
    this.form.reset();
  }

  close() {
    this.modalCtrl.dismiss(undefined, 'cancel', this.modalId);
  }

  /** Enter in einem Textfeld schließt nur die Tastatur und wendet nichts an. */
  blurInput(event: Event) {
    (event.target as HTMLElement | null)?.blur();
  }

  displayDate(field: DateField): string | null {
    const value = this.form.controls[field].value;
    return value ? formatDisplayDate(value) : null;
  }

  /** „Datum bis“ frühestens „Datum von“. */
  minFor(field: DateField): string | undefined {
    return field === 'endDate' ? this.form.controls.startDate.value ?? undefined : undefined;
  }

  /** „Datum von“ höchstens „Datum bis“. */
  maxFor(field: DateField): string | undefined {
    return field === 'startDate' ? this.form.controls.endDate.value ?? undefined : undefined;
  }

  /** Startwert des Pickers: der Formularwert, sonst heute (innerhalb von min/max). */
  pickerValue(field: DateField): string {
    const value = this.form.controls[field].value;
    if (value) return value;
    const today = toIsoDay(new Date());
    const min = this.minFor(field);
    const max = this.maxFor(field);
    if (min && today < min) return min;
    if (max && today > max) return max;
    return today;
  }

  openDatePicker(field: DateField) {
    this.openPicker.set(field);
  }

  /** „Speichern“: Auswahl zuerst bestätigen (sonst steht in `value` noch der alte Tag), dann übernehmen. */
  async pickDate(field: DateField, datetime: DatePickerRef) {
    await datetime.confirm(false);
    const value = datetime.value;
    if (typeof value === 'string' && value) {
      this.setDate(field, isoDayOf(value));
    }
    this.openPicker.set(null);
  }

  /** „Reset“ im Picker: nur dieses Datumsfeld leeren. */
  clearDate(field: DateField) {
    this.setDate(field, null);
    this.openPicker.set(null);
  }

  /** „Abbrechen“ im Picker: Formular unverändert. */
  closeDatePicker() {
    this.openPicker.set(null);
  }

  /**
   * Tipp neben den Kalender: Die Picker-Modals sind (wie alle Datums-Picker der App, global.scss) vollflächig mit
   * transparentem ion-content, der echte Backdrop ist dadurch nicht erreichbar. Ein Tipp auf die leere Content-Fläche
   * selbst (nicht auf den Kalender darin) schließt daher wie „Abbrechen“; das Formular bleibt unverändert.
   */
  onPickerAreaClick(event: Event) {
    if (event.target === event.currentTarget) {
      this.closeDatePicker();
    }
  }

  /** Picker auf beliebigem Weg geschlossen (auch Backdrop/Hardware-Back): Zustand zurücksetzen, damit er wieder öffnet. */
  onPickerDismissed() {
    this.openPicker.set(null);
  }

  private setDate(field: DateField, value: string | null) {
    const control = this.form.controls[field];
    control.setValue(value);
    control.markAsDirty();
  }
}
