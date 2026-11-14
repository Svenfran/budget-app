import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from 'src/app/auth/auth.service';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { GroupService } from 'src/app/service/group.service';
import { aGroup, aLoggedInUser, aUser, REFERENCE_DATE } from 'src/testing/test-data';
import { authServiceStub, provideServiceTesting } from 'src/testing/test-providers';
import { toIsoDay } from '../iso-day';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import { CartService } from '../service/cart.service';
import { settlementSheetModalOptions } from './settlement-sheet-options';
import { SettlementDatePickerRef, SettlementSheetComponent } from './settlement-sheet.component';

describe('SettlementSheetComponent (DIV-70)', () => {
  let fixture: ComponentFixture<SettlementSheetComponent>;
  let component: SettlementSheetComponent;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let addSettlementPayment: jasmine.Spy;
  let getGroupMembers: jasmine.Spy;
  let getHistory: jasmine.Spy;
  let groupMembers: WritableSignal<GroupMembers>;
  let history: WritableSignal<Zeitraum[]>;
  let saving: WritableSignal<boolean>;
  let dirty: WritableSignal<boolean>;

  const MODAL_ID = 'settlement-sheet-42';
  const today = toIsoDay(REFERENCE_DATE);
  const me = aLoggedInUser({ id: 50, name: 'Ich' });
  const owner = aUser({ userName: 'Olga' });
  const anna = aUser({ userName: 'Anna' });
  const group = aGroup({ id: 7, name: 'WG', dateCreated: new Date('2025-01-01T12:00:00Z') });
  const members = (list = [{ id: me.id, userName: 'Ich' }, anna]): GroupMembers =>
    ({ id: group.id, name: group.name, ownerId: owner.id, ownerName: owner.userName, members: list });
  const key = (suffix: string) => `domains.spendings.overlay.settlementpayment.${suffix}`;

  const el = (): HTMLElement => fixture.nativeElement;
  const button = (cls: string) => el().querySelector(`ion-button.${cls}`) as HTMLIonButtonElement | null;
  const detect = () => fixture.detectChanges();

  /** ion-datetime-Ersatz: `value` liefert erst nach `confirm()` die angeklickte Auswahl (wie Ionic mit eigenen Buttons). */
  const datetime = (shown: string | null, selected: string | null = shown) => {
    let confirmed = false;
    const confirm = jasmine.createSpy('confirm').and.callFake(async () => { confirmed = true; });
    const ref = { confirm, get value() { return confirmed ? selected : shown; } };
    return ref as unknown as SettlementDatePickerRef & { confirm: jasmine.Spy };
  };

  /** Steuerbares Ergebnis des Service-Aufrufs. */
  const pendingSave = () => {
    let settle!: (ok: boolean) => void;
    let fail!: (err: unknown) => void;
    addSettlementPayment.and.returnValue(new Promise<boolean>((resolve, reject) => { settle = resolve; fail = reject; }));
    return { settle: (ok: boolean) => settle(ok), fail: (err: unknown) => fail(err) };
  };

  const fillValid = () => {
    component.form.setValue({ amount: 25.5, memberId: anna.id, datePurchased: '2025-06-10' });
    detect();
  };

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(REFERENCE_DATE);
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    modalCtrl.dismiss.and.resolveTo(true);
    addSettlementPayment = jasmine.createSpy('addSettlementPayment').and.resolveTo(true);
    getGroupMembers = jasmine.createSpy('getGroupMembers');
    getHistory = jasmine.createSpy('getGroupMembershipHistoryForGroupAndUser');
    groupMembers = signal(members());
    history = signal<Zeitraum[]>([]);
    saving = signal(false);
    dirty = signal(false);
    TestBed.configureTestingModule({
      declarations: [SettlementSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ModalController, useValue: modalCtrl },
        { provide: CartService, useValue: { addSettlementPayment } },
        { provide: AuthService, useValue: authServiceStub(me) },
        {
          provide: GroupService, useValue: {
            activeGroup: signal(group),
            groupMembers,
            groupMembershipHistory: history,
            getGroupMembers,
            getGroupMembershipHistoryForGroupAndUser: getHistory,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(SettlementSheetComponent);
    component = fixture.componentInstance;
    component.modalId = MODAL_ID;
    component.saving = saving;
    component.dirty = dirty;
    detect();
  });

  afterEach(() => jasmine.clock().uninstall());

  it('lädt beim Öffnen Mitglieder und Mitgliedschaftshistorie der aktiven Gruppe genau einmal', () => {
    expect(getGroupMembers).toHaveBeenCalledOnceWith(7);
    expect(getHistory).toHaveBeenCalledOnceWith(group);
  });

  it('Titel, Untertitel und vorausgewähltes Datum heute (AC 1, AC 2)', () => {
    expect(el().textContent).toContain(key('title'));
    expect(el().textContent).toContain(key('subtitle'));
    expect(component.form.controls.datePurchased.value).toBe(today);
    expect(el().querySelector('.date-value')?.textContent).toContain('15.06.2025');
  });

  it('Datumsgrenzen wie bisher: Gründungstag der Gruppe bis heute', () => {
    expect(component.minDate).toBe('2025-01-01');
    expect(component.maxDate).toBe(today);
  });

  it('bietet Owner und Mitglieder ohne mich mit vollem Namen an', () => {
    const options = Array.from(el().querySelectorAll('ion-select-option')).map(o => o.textContent?.trim());
    expect(options).toEqual(['Olga', 'Anna']);
  });

  it('ohne Empfänger: Hinweis statt Formular, kein „Speichern“, Schließen über das X', () => {
    groupMembers.set(members([]));
    detect();

    expect(el().querySelector('.no-members')?.textContent).toContain(key('no_members'));
    expect(el().querySelector('.settlement-form')).toBeNull();
    expect(button('btn-save')).toBeNull();
    expect(button('btn-close')).not.toBeNull();
  });

  // Nutzerwunsch (Feedback Phase 2): „Abbrechen“ doppelte das X; nur noch „Speichern“ in voller Breite
  it('Footer enthält nur „Speichern“ in voller Breite, kein „Abbrechen“', () => {
    expect(button('btn-cancel')).toBeNull();
    expect(el().querySelectorAll('.sheet-actions ion-button').length).toBe(1);
    expect(button('btn-save')!.expand).toBe('block');
  });

  describe('Validierung (AC 3–5)', () => {
    it('„Speichern“ ist ohne Betrag oder Empfänger gesperrt und ohne Wirkung', async () => {
      expect(button('btn-save')!.disabled).toBeTrue();

      component.form.patchValue({ amount: 10 });
      detect();
      expect(button('btn-save')!.disabled).toBeTrue();

      await component.save();
      expect(addSettlementPayment).not.toHaveBeenCalled();
    });

    it('ungültiger Betrag sperrt „Speichern“ und zeigt nach Bedienung den bisherigen Fehlertext', () => {
      component.form.patchValue({ amount: 'abc' as unknown as number, memberId: anna.id });
      detect();
      expect(button('btn-save')!.disabled).toBeTrue();
      expect(el().querySelector('.error-amount')).toBeNull(); // noch nicht bedient

      component.form.controls.amount.markAsTouched();
      detect();
      expect(el().querySelector('.error-amount')?.textContent).toContain('global.errors.invalid_amount');
    });

    // Feedback Phase 2: ion-select meldet ionBlur (→ touched) schon beim Öffnen der Auswahl
    it('Öffnen der Empfänger-Auswahl zeigt noch keinen Fehler', () => {
      component.form.controls.memberId.markAsTouched();
      detect();
      expect(el().querySelector('.error-member')).toBeNull();
    });

    it('Auswahl ohne Empfänger geschlossen: „Pflichtfeld“', () => {
      el().querySelector('ion-select.select-member')!.dispatchEvent(new CustomEvent('ionDismiss'));
      detect();
      expect(el().querySelector('.error-member')?.textContent).toContain('global.errors.required');
    });

    it('Auswahl mit Empfänger geschlossen: kein Fehler', () => {
      component.form.controls.memberId.setValue(anna.id);
      el().querySelector('ion-select.select-member')!.dispatchEvent(new CustomEvent('ionDismiss'));
      detect();
      expect(el().querySelector('.error-member')).toBeNull();
    });

    it('Betragsfeld ohne Autofokus (Feedback Phase 2)', () => {
      expect(el().querySelector('ion-input.input-amount')!.hasAttribute('autofocus')).toBeFalse();
      expect((el().querySelector('ion-input.input-amount') as HTMLIonInputElement).autofocus).toBeFalse();
    });

    it('gültige Eingaben geben „Speichern“ frei', () => {
      fillValid();
      expect(button('btn-save')!.disabled).toBeFalse();
    });
  });

  describe('Speichern (FA3)', () => {
    it('übergibt den Request wie bisher und schließt nach Erfolg das eigene Modal mit Rolle saved (AC 6)', async () => {
      fillValid();

      await component.save();

      expect(addSettlementPayment).toHaveBeenCalledOnceWith({
        amount: 25.5, groupId: 7, member: { id: anna.id, userName: 'Anna' }, datePurchased: new Date('2025-06-10'),
      });
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, 'saved', MODAL_ID);
      expect(saving()).toBeFalse();
    });

    it('zeigt während des Speicherns den Ladezustand und sperrt Felder und Aktionen', async () => {
      fillValid();
      const request = pendingSave();

      const saved = component.save();
      detect();

      expect(saving()).toBeTrue();
      expect(component.form.disabled).toBeTrue();
      expect(el().querySelector('ion-button.btn-save ion-spinner')).not.toBeNull();
      expect(button('btn-save')!.disabled).toBeTrue();
      expect(button('btn-close')!.disabled).toBeTrue();

      request.settle(true);
      await saved;
      expect(saving()).toBeFalse();
    });

    it('verhindert mehrfaches Absenden', async () => {
      fillValid();
      const request = pendingSave();

      const first = component.save();
      await component.save();
      request.settle(true);
      await first;

      expect(addSettlementPayment).toHaveBeenCalledTimes(1);
    });

    it('Fehler: Sheet bleibt offen, Eingaben bleiben erhalten und sind wieder bearbeitbar (AC 7)', async () => {
      fillValid();
      addSettlementPayment.and.resolveTo(false);

      await component.save();
      detect();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(component.form.getRawValue()).toEqual({ amount: 25.5, memberId: anna.id, datePurchased: '2025-06-10' });
      expect(component.form.enabled).toBeTrue();
      expect(saving()).toBeFalse();
      expect(button('btn-save')!.disabled).toBeFalse();
    });

    it('unerwartet zurückgewiesenes Promise: wie ein Fehler, Ladezustand zurückgesetzt, Eingaben erhalten', async () => {
      fillValid();
      const request = pendingSave();
      spyOn(console, 'error');

      const saved = component.save();
      request.fail(new Error('boom'));
      await saved;

      expect(saving()).toBeFalse();
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(component.form.getRawValue()).toEqual({ amount: 25.5, memberId: anna.id, datePurchased: '2025-06-10' });
    });

    // Codex-Review 2, F-001: simuliert eine neu geladene Liste (direktes Setzen des Signals), keine WebSocket-Sync.
    it('Empfänger fehlt nach neu geladener Liste: kein Fehler, Request mit leerem Namen, Eingaben bleiben', async () => {
      fillValid();
      groupMembers.set(members([{ id: me.id, userName: 'Ich' }]));
      detect();

      await component.save();

      expect(addSettlementPayment).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        member: { id: anna.id, userName: '' },
      }));
      expect(component.form.controls.memberId.value).toBe(anna.id);
    });
  });

  it('X schließt das eigene Modal ohne Daten', () => {
    button('btn-close')!.click();

    expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, 'cancel', MODAL_ID);
  });

  describe('Datumsauswahl', () => {
    const pickerModal = () => el().querySelector('ion-modal.settlement-date-picker') as HTMLIonModalElement;
    const dateItem = () => el().querySelector('ion-item.date-field') as HTMLElement;

    it('Tipp auf das Datumsfeld öffnet den Picker mit der app-weiten Picker-Klasse', () => {
      dateItem().click();
      detect();

      expect(component.openPicker()).toBeTrue();
      expect(pickerModal().isOpen).toBeTrue();
      expect(pickerModal().classList).toContain('date-picker-modal');
    });

    it('„OK“ bestätigt die Auswahl vor dem Lesen und übernimmt den neu gewählten Tag', async () => {
      component.openDatePicker();
      const picker = datetime(today, '2025-06-12T00:00:00');

      await component.pickDate(picker);
      detect();

      expect(picker.confirm).toHaveBeenCalledOnceWith(false);
      expect(component.form.controls.datePurchased.value).toBe('2025-06-12');
      expect(component.form.controls.datePurchased.dirty).toBeTrue();
      expect(component.openPicker()).toBeFalse();
      expect(el().querySelector('.date-value')?.textContent).toContain('12.06.2025');
    });

    it('„Abbrechen“ lässt das Datum unverändert; wieder geöffnet zeigt der Picker den Formularwert', () => {
      component.form.patchValue({ datePurchased: '2025-06-10' });
      component.openDatePicker();

      component.closeDatePicker();

      expect(component.openPicker()).toBeFalse();
      expect(component.form.controls.datePurchased.value).toBe('2025-06-10');
      component.openDatePicker();
      expect(component.pickerValue()).toBe('2025-06-10');
    });

    it('Picker von außen geschlossen (Backdrop/Hardware-Back): Datum unverändert, das Feld öffnet wieder', () => {
      dateItem().click();
      detect();

      pickerModal().dispatchEvent(new CustomEvent('didDismiss'));
      detect();

      expect(component.openPicker()).toBeFalse();
      expect(pickerModal().isOpen).toBeFalse();
      expect(component.form.controls.datePurchased.value).toBe(today);

      dateItem().click();
      detect();
      expect(component.openPicker()).toBeTrue();
      expect(pickerModal().isOpen).toBeTrue();
    });

    it('nur Tage der eigenen Mitgliedschaft in dieser Gruppe sind wählbar (wie bisher)', () => {
      history.set([
        { startDate: '2025-03-01', endDate: null, userId: me.id, groupId: 7 },
        { startDate: '2025-01-01', endDate: null, userId: anna.id, groupId: 7 },
      ] as unknown as Zeitraum[]);

      expect(component.isDateEnabled('2025-03-01')).toBeTrue();
      expect(component.isDateEnabled('2025-02-28')).toBeFalse();
    });
  });

  // Phase 3 (E1): „geändert“ für die Rückfrage beim Schließen
  describe('dirty', () => {
    const amountInput = () => el().querySelector('ion-input.input-amount') as HTMLIonInputElement;
    const typeAmount = (value: string) => {
      (amountInput() as unknown as { value: string }).value = value;
      amountInput().dispatchEvent(new CustomEvent('ionInput', { bubbles: true }));
      detect();
    };

    it('frisches Sheet ist unverändert (Startdatum heute setzt nichts)', () => {
      expect(dirty()).toBeFalse();
      expect(component.form.controls.datePurchased.value).toBe(today);
    });

    it('Eingabe eines Betrags macht das Sheet geändert', () => {
      typeAmount('12');

      expect(component.form.controls.amount.value).toBe(12);
      expect(dirty()).toBeTrue();
    });

    // Regression Codex-Review 1, F-001: setValue() + markAsDirty() – valueChanges allein sähe das nicht
    it('nur das Datum über den Picker geändert: geändert, und Schließen fragt nach', async () => {
      const confirmDiscard = jasmine.createSpy('confirmDiscard').and.resolveTo(false);
      const canDismiss = settlementSheetModalOptions({ saving, dirty, confirmDiscard, dismiss: jasmine.createSpy('dismiss') }).canDismiss as
        (data?: unknown, role?: string) => Promise<boolean>;

      await component.pickDate(datetime(today, '2025-06-12T00:00:00'));

      expect(dirty()).toBeTrue();
      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      expect(confirmDiscard).toHaveBeenCalledTimes(1);
    });

    it('Picker geöffnet und abgebrochen oder von außen geschlossen: bleibt unverändert', () => {
      component.openDatePicker();
      component.closeDatePicker();
      component.openDatePicker();
      component.onPickerDismissed();

      expect(dirty()).toBeFalse();
    });

    it('Sperren und Freigeben beim Speichern ändert den Zustand nicht', async () => {
      addSettlementPayment.and.resolveTo(false);
      component.form.setValue({ amount: 10, memberId: anna.id, datePurchased: today });
      expect(dirty()).toBeFalse();

      await component.save();

      expect(dirty()).toBeFalse();
    });
  });
});
