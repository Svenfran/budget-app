import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { aCategory, aGroup } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
import { CartFilter } from '../model/cart-filter';
import { DatePickerRef, FilterSheetComponent } from './filter-sheet.component';

describe('FilterSheetComponent (DIV-71)', () => {
  let fixture: ComponentFixture<FilterSheetComponent>;
  let component: FilterSheetComponent;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let getGroupMembers: jasmine.Spy;
  let getCategoriesByGroup: jasmine.Spy;

  const MODAL_ID = 'filter-sheet-42';
  const group = aGroup({ id: 7, name: 'WG' });
  const noCriteria: CartFilter = {
    title: null, description: null, category: null, startDate: null, endDate: null, userName: null,
  };
  const key = (suffix: string) => `domains.spendings.overlay.filter.${suffix}`;

  const el = (): HTMLElement => fixture.nativeElement;
  const button = (cls: string) => el().querySelector(`ion-button.${cls}`) as HTMLIonButtonElement;
  const dateItem = (field: string) => el().querySelector(`ion-item.date-${field}`) as HTMLElement;
  const pickerModal = (field: string) => el().querySelector(`ion-modal.picker-${field}`) as HTMLIonModalElement;
  const click = (element: HTMLElement) => {
    element.click();
    fixture.detectChanges();
  };

  /** ion-datetime-Ersatz: `value` liefert erst nach `confirm()` die angeklickte Auswahl (wie Ionic mit eigenen Buttons). */
  const datetime = (shown: string | null, selected: string | null = shown) => {
    let confirmed = false;
    const confirm = jasmine.createSpy('confirm').and.callFake(async () => { confirmed = true; });
    const ref = { confirm, get value() { return confirmed ? selected : shown; } };
    return ref as unknown as DatePickerRef & { confirm: jasmine.Spy };
  };

  const render = (cartFilter: CartFilter = {}) => {
    component.cartFilter = cartFilter;
    component.modalId = MODAL_ID;
    fixture.detectChanges();
  };

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2025, 5, 15, 12, 0)); // „heute“ für den Picker-Startwert
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    modalCtrl.dismiss.and.resolveTo(true);
    getGroupMembers = jasmine.createSpy('getGroupMembers');
    getCategoriesByGroup = jasmine.createSpy('getCategoriesByGroup');
    TestBed.configureTestingModule({
      declarations: [FilterSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ModalController, useValue: modalCtrl },
        {
          provide: GroupService, useValue: {
            activeGroup: signal(group),
            groupMembersWithOwner: signal([{ id: 1, userName: 'Sven' }, { id: 2, userName: 'Anna' }]),
            getGroupMembers,
          },
        },
        {
          provide: CategoryService, useValue: {
            categories: signal([aCategory({ name: 'Lebensmittel' }), aCategory({ name: 'Drogerie' })]),
            getCategoriesByGroup,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(FilterSheetComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => jasmine.clock().uninstall());

  it('belegt das Formular mit dem angewendeten Filter vor und zeigt die Datumswerte (AC 3)', () => {
    render({
      title: 'Edeka', description: 'Woche', category: ['Drogerie'], userName: ['Anna'],
      startDate: new Date(2025, 5, 1), endDate: new Date(2025, 5, 30),
    });

    expect(component.form.getRawValue()).toEqual({
      title: 'Edeka', description: 'Woche', categories: ['Drogerie'], members: ['Anna'],
      startDate: '2025-06-01', endDate: '2025-06-30',
    });
    expect(dateItem('startDate').textContent).toContain('01.06.2025');
    expect(dateItem('endDate').textContent).toContain('30.06.2025');
  });

  it('leerer Filter: leeres Formular, Datumsfelder zeigen ihre Platzhalter', () => {
    render();

    expect(component.form.getRawValue()).toEqual({
      title: null, description: null, categories: null, members: null, startDate: null, endDate: null,
    });
    expect(dateItem('startDate').textContent).toContain(key('date_from'));
    expect(dateItem('endDate').textContent).toContain(key('date_to'));
  });

  it('lädt Mitglieder und Kategorien der aktiven Gruppe genau einmal beim Öffnen', () => {
    render();

    expect(getGroupMembers).toHaveBeenCalledOnceWith(7);
    expect(getCategoriesByGroup).toHaveBeenCalledOnceWith(group);
  });

  it('„Filter anwenden“ schließt das eigene Modal mit dem normalisierten Filter und Rolle apply (AC 5)', () => {
    render();
    component.form.patchValue({ title: '  Edeka ', categories: ['Lebensmittel'], members: [], startDate: '2025-06-01' });
    fixture.detectChanges();

    click(button('btn-apply'));

    expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(
      { ...noCriteria, title: 'Edeka', category: ['Lebensmittel'], startDate: new Date(2025, 5, 1) }, 'apply', MODAL_ID);
  });

  it('„Reset“ leert nur die Felder und schließt nicht (AC 6, E1)', () => {
    render({ title: 'Edeka', category: ['Drogerie'], startDate: new Date(2025, 5, 1) });

    click(button('btn-reset'));

    expect(component.form.getRawValue()).toEqual({
      title: null, description: null, categories: null, members: null, startDate: null, endDate: null,
    });
    expect(modalCtrl.dismiss).not.toHaveBeenCalled();
  });

  it('nach „Reset“ hebt „Filter anwenden“ den Filter auf (alles null)', () => {
    render({ title: 'Edeka' });

    click(button('btn-reset'));
    click(button('btn-apply'));

    expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(noCriteria, 'apply', MODAL_ID);
  });

  it('X schließt das eigene Modal ohne Daten (Abbrechen, AC 7)', () => {
    render({ title: 'Edeka' });
    component.form.patchValue({ title: 'Rossmann' });

    click(button('btn-close'));

    expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, 'cancel', MODAL_ID);
  });

  it('verändert den übergebenen (angewendeten) Filter nicht', () => {
    const applied: CartFilter = { title: 'Edeka', category: ['Drogerie'], userName: ['Anna'], startDate: new Date(2025, 5, 1) };
    render(applied);

    component.form.controls.categories.value!.push('Lebensmittel');
    component.form.patchValue({ title: 'Rossmann', members: ['Sven'] });
    click(button('btn-reset'));
    click(button('btn-apply'));

    expect(applied).toEqual({ title: 'Edeka', category: ['Drogerie'], userName: ['Anna'], startDate: new Date(2025, 5, 1) });
  });

  describe('ungültiger Datumsbereich (AC 9)', () => {
    beforeEach(() => {
      render();
      component.form.patchValue({ startDate: '2025-06-30', endDate: '2025-06-01' });
      fixture.detectChanges();
    });

    it('Hinweis sichtbar, „Filter anwenden“ deaktiviert und ohne Wirkung', () => {
      expect(component.form.hasError('dateRange')).toBeTrue();
      expect(el().querySelector('.date-range-error')?.textContent).toContain(key('error_date_range'));
      expect(button('btn-apply').disabled).toBeTrue();

      component.apply();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    it('gültiger Bereich (auch gleicher Tag) blendet den Hinweis aus und gibt „Filter anwenden“ frei', () => {
      component.form.patchValue({ endDate: '2025-06-30' });
      fixture.detectChanges();

      expect(el().querySelector('.date-range-error')).toBeNull();
      expect(button('btn-apply').disabled).toBeFalse();
    });
  });

  describe('Datumsauswahl', () => {
    beforeEach(() => render());

    it('Tipp auf ein Datumsfeld öffnet genau dessen Picker', () => {
      click(dateItem('endDate'));

      expect(component.openPicker()).toBe('endDate');
      expect(pickerModal('endDate').isOpen).toBeTrue();
      expect(pickerModal('startDate').isOpen).toBeFalse();
    });

    // Codex-Review 1, F-002: ohne confirm() stünde in value noch der zuvor angezeigte Tag
    it('„Speichern“ bestätigt die Auswahl vor dem Lesen und übernimmt den neu gewählten Tag', async () => {
      component.openDatePicker('startDate');
      const picker = datetime('2025-06-15', '2025-06-12T00:00:00');

      await component.pickDate('startDate', picker);

      expect(picker.confirm).toHaveBeenCalledOnceWith(false);
      expect(component.form.controls.startDate.value).toBe('2025-06-12');
      expect(component.openPicker()).toBeNull();
    });

    it('„Speichern“ ohne Tag-Klick übernimmt den angezeigten Startwert (heute)', async () => {
      component.openDatePicker('startDate');
      const shown = component.pickerValue('startDate');

      await component.pickDate('startDate', datetime(shown));

      expect(shown).toBe('2025-06-15');
      expect(component.form.controls.startDate.value).toBe('2025-06-15');
    });

    it('„Abbrechen“ lässt das Formular unverändert; wieder geöffnet zeigt der Picker den Formularwert', () => {
      component.form.patchValue({ startDate: '2025-06-10' });
      component.openDatePicker('startDate');

      component.closeDatePicker();
      fixture.detectChanges();

      expect(component.openPicker()).toBeNull();
      expect(component.form.controls.startDate.value).toBe('2025-06-10');

      // Codex-Review 2: verworfene Auswahl erscheint beim erneuten Öffnen nicht (Startwert = Formularwert)
      click(dateItem('startDate'));
      expect(component.openPicker()).toBe('startDate');
      expect(component.pickerValue('startDate')).toBe('2025-06-10');
    });

    it('„Reset“ im Picker leert nur dieses Datumsfeld', () => {
      component.form.patchValue({ startDate: '2025-06-10', endDate: '2025-06-20' });
      component.openDatePicker('startDate');

      component.clearDate('startDate');
      fixture.detectChanges();

      expect(component.form.controls.startDate.value).toBeNull();
      expect(component.form.controls.endDate.value).toBe('2025-06-20');
      expect(component.openPicker()).toBeNull();
      expect(dateItem('startDate').textContent).toContain(key('date_from'));
    });

    // Codex-Review 1, F-003: Ionic setzt isOpen beim Schließen von außen nicht zurück
    it('Picker von außen geschlossen (Backdrop/Hardware-Back): Formular unverändert, dasselbe Feld öffnet wieder', () => {
      component.form.patchValue({ endDate: '2025-06-20' });
      click(dateItem('endDate'));

      pickerModal('endDate').dispatchEvent(new CustomEvent('didDismiss'));
      fixture.detectChanges();

      expect(component.openPicker()).toBeNull();
      expect(pickerModal('endDate').isOpen).toBeFalse();
      expect(component.form.controls.endDate.value).toBe('2025-06-20');

      click(dateItem('endDate'));

      expect(component.openPicker()).toBe('endDate');
      expect(pickerModal('endDate').isOpen).toBeTrue();
    });

    it('min/max begrenzen den jeweils anderen Picker (E2)', () => {
      expect(component.minFor('endDate')).toBeUndefined();
      expect(component.maxFor('startDate')).toBeUndefined();

      component.form.patchValue({ startDate: '2025-06-10', endDate: '2025-06-20' });

      expect(component.minFor('endDate')).toBe('2025-06-10');
      expect(component.maxFor('startDate')).toBe('2025-06-20');
      expect(component.minFor('startDate')).toBeUndefined();
      expect(component.maxFor('endDate')).toBeUndefined();
    });

    it('Startwert ohne Formularwert ist heute, aber innerhalb von min/max', () => {
      expect(component.pickerValue('endDate')).toBe('2025-06-15');

      component.form.patchValue({ startDate: '2025-07-01' });
      expect(component.pickerValue('endDate')).toBe('2025-07-01');

      component.form.patchValue({ startDate: null, endDate: '2025-06-01' });
      expect(component.pickerValue('startDate')).toBe('2025-06-01');
    });

    it('„Speichern“ ohne Wert im Picker ändert das Formular nicht', async () => {
      component.form.patchValue({ startDate: '2025-06-10' });

      await component.pickDate('startDate', datetime(null));

      expect(component.form.controls.startDate.value).toBe('2025-06-10');
    });
  });

  it('Enter im Textfeld schließt nur die Tastatur und wendet nichts an (FA3)', () => {
    render();
    spyOn(component, 'blurInput').and.callThrough();

    el().querySelector('ion-input.input-title')!
      .dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true }));

    expect(component.blurInput).toHaveBeenCalled();
    expect(modalCtrl.dismiss).not.toHaveBeenCalled();
  });

  it('blurInput nimmt dem Feld den Fokus', () => {
    render();
    const input = document.createElement('input');
    document.body.appendChild(input);
    input.focus();

    component.blurInput({ target: input } as unknown as Event);

    expect(document.activeElement).not.toBe(input);
    input.remove();
  });

  it('bietet Kategorien und Ersteller der Gruppe als Mehrfachauswahl an', () => {
    render();

    const selects = el().querySelectorAll('ion-select');
    const options = (index: number) => Array.from(selects[index].querySelectorAll('ion-select-option'))
      .map(option => (option as HTMLIonSelectOptionElement).value);

    expect((selects[0] as HTMLIonSelectElement).multiple).toBeTrue();
    expect(options(0)).toEqual(['Lebensmittel', 'Drogerie']);
    expect((selects[1] as HTMLIonSelectElement).multiple).toBeTrue();
    expect(options(1)).toEqual(['Sven', 'Anna']);
  });
});
