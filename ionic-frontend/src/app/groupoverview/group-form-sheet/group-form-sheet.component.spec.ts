import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { AlertController, IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GroupService } from 'src/app/service/group.service';
import { ionicReady } from 'src/testing/ionic-ready';
import { aGroupOverview } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
import { GROUP_GONE_ROLE, GROUP_SHEET_SAVED_ROLE } from '../group-sheet-roles';
import { GroupOverview } from '../model/group-overview';
import { GroupFormSheetComponent, GroupFormSheetMode } from './group-form-sheet.component';

describe('GroupFormSheetComponent (DIV-80)', () => {
  let fixture: ComponentFixture<GroupFormSheetComponent>;
  let component: GroupFormSheetComponent;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let addGroup: jasmine.Spy;
  let updateGroup: jasmine.Spy;
  let alertCreate: jasmine.Spy;
  /** Antworten der Bestätigungsdialoge in Reihenfolge ('confirm' | 'cancel'); fehlt eine, bleibt der Dialog offen. */
  let alertRoles: string[];
  let alerts: { present: jasmine.Spy; dismiss: jasmine.Spy; remove: jasmine.Spy }[];
  let overview: WritableSignal<GroupOverview[]>;
  let saving: WritableSignal<boolean>;

  const MODAL_ID = 'group-form-sheet-42';
  const wg = aGroupOverview({ id: 7, name: 'WG', dateCreated: new Date('2024-01-01T00:00:00Z') });
  const el = (): HTMLElement => fixture.nativeElement;
  const button = (cls: string) => el().querySelector(`ion-button.${cls}`) as HTMLIonButtonElement;

  /** Steuerbares Ergebnis des Service-Aufrufs (addGroup: boolean, updateGroup: UpdateGroupResult). */
  const pending = (spy: jasmine.Spy) => {
    let settle!: (result: unknown) => void;
    spy.and.returnValue(new Promise(resolve => settle = resolve));
    return (result: unknown) => settle(result);
  };

  function create(mode: GroupFormSheetMode, group?: GroupOverview) {
    fixture = TestBed.createComponent(GroupFormSheetComponent);
    component = fixture.componentInstance;
    component.modalId = MODAL_ID;
    component.mode = mode;
    component.group = group;
    component.saving = saving;
    fixture.detectChanges();
  }

  beforeEach(() => {
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    modalCtrl.dismiss.and.resolveTo(true);
    addGroup = jasmine.createSpy('addGroup').and.resolveTo(true);
    updateGroup = jasmine.createSpy('updateGroup').and.resolveTo('ok');
    alertRoles = [];
    alerts = [];
    // Ionic-Alert-Ersatz: onDidDismiss löst mit der nächsten vorgegebenen Rolle auf
    alertCreate = jasmine.createSpy('create').and.callFake(async () => {
      const role = alertRoles.shift();
      let close!: (value: { role?: string }) => void;
      const dismissed = new Promise<{ role?: string }>(resolve => close = resolve);
      const alert = {
        present: jasmine.createSpy('present').and.callFake(async () => { if (role) close({ role }); }),
        onDidDismiss: () => dismissed,
        dismiss: jasmine.createSpy('dismiss').and.callFake(async () => close({ role: 'backdrop' })),
        remove: jasmine.createSpy('remove'),
      };
      alerts.push(alert);
      return alert;
    });
    overview = signal([wg]);
    saving = signal(false);
    TestBed.configureTestingModule({
      declarations: [GroupFormSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ModalController, useValue: modalCtrl },
        { provide: AlertController, useValue: { create: alertCreate } },
        { provide: GroupService, useValue: { groupOverviewList: overview, addGroup, updateGroup } },
      ],
    });
  });

  describe('Gruppe hinzufügen', () => {
    beforeEach(() => create('create'));

    it('zeigt Titel „Gruppe hinzufügen“, leeres Feld und „Erstellen“', () => {
      expect(el().querySelector('h2')!.textContent!.trim()).toBe('groups.form_sheet.title_create');
      expect(el().querySelector('.sheet-subtitle')).toBeNull();
      expect(component.form.getRawValue()).toEqual({ name: '', currencyCode: 'EUR' });
      expect(button('btn-save').textContent!.trim()).toBe('groups.form_sheet.btn_create');
    });

    it('speichert den getrimmten Namen über addGroup und schließt nach Erfolg mit Rolle saved', async () => {
      component.form.patchValue({ name: '  Verein ' });

      await component.save();

      expect(addGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Verein' }), undefined);
      expect(updateGroup).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
      expect(saving()).toBeFalse();
    });

    it('gibt die aufrufende Seite an addGroup weiter („Keine Gruppe“ navigiert danach zur Übersicht, DIV-77)', async () => {
      component.callingPage = '/no-group';
      component.form.patchValue({ name: 'Verein' });

      await component.save();

      expect(addGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Verein' }), '/no-group');
    });

    it('leeres Feld verlassen (z. B. Tipp aufs X): kein Feldfehler, damit sich der Kopf nicht verschiebt', () => {
      el().querySelector('ion-input.group-name-input')!.dispatchEvent(new CustomEvent('ionBlur'));
      fixture.detectChanges();

      expect(component.name.touched).toBeTrue();
      expect(el().querySelector('.error-name')).toBeNull();
    });

    it('Name eingegeben und wieder gelöscht: Feldfehler sofort sichtbar', () => {
      component.name.setValue('');
      component.name.markAsDirty();
      fixture.detectChanges();

      expect(el().querySelector('.error-name')!.textContent!.trim()).toBe('groups.form_sheet.name_required');
    });

    it('leerer Name: kein Request, Feldfehler sichtbar', async () => {
      component.form.patchValue({ name: '   ' });

      await component.save();
      fixture.detectChanges();

      expect(addGroup).not.toHaveBeenCalled();
      expect(el().querySelector('.error-name')!.textContent!.trim()).toBe('groups.form_sheet.name_required');
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    it('Fehler: Sheet bleibt mit der Eingabe offen und ist wieder bedienbar', async () => {
      addGroup.and.resolveTo(false);
      component.form.patchValue({ name: 'Verein' });

      await component.save();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(component.form.getRawValue()).toEqual({ name: 'Verein', currencyCode: 'EUR' });
      expect(component.form.enabled).toBeTrue();
      expect(saving()).toBeFalse();
    });

    it('sperrt während des Speicherns einen zweiten Versuch (Doppel-Tipp, Enter)', async () => {
      const settle = pending(addGroup);
      component.form.patchValue({ name: 'Verein' });

      const first = component.save();
      expect(saving()).toBeTrue();
      await component.save();
      fixture.detectChanges();
      expect(button('btn-save').disabled).toBeTrue();
      expect(button('btn-cancel').disabled).toBeTrue();

      settle(true);
      await first;
      expect(addGroup).toHaveBeenCalledTimes(1);
    });

    it('„Abbrechen“ schließt ohne Request', () => {
      button('btn-cancel').click();

      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, 'cancel', MODAL_ID);
      expect(addGroup).not.toHaveBeenCalled();
    });
  });

  describe('Gruppe bearbeiten', () => {
    beforeEach(() => create('edit', wg));

    it('zeigt Titel, aktuellen Namen als Untertitel, vorbelegtes Feld und „Speichern“', () => {
      expect(el().querySelector('h2')!.textContent!.trim()).toBe('groups.form_sheet.title_edit');
      expect(el().querySelector('.sheet-subtitle')!.textContent!.trim()).toBe('WG');
      expect(component.form.getRawValue()).toEqual({ name: 'WG', currencyCode: 'EUR' });
      expect(button('btn-save').textContent!.trim()).toBe('groups.form_sheet.btn_save');
    });

    it('speichert über updateGroup mit ID und Gründungsdatum der Gruppe', async () => {
      component.form.patchValue({ name: ' Wohngemeinschaft ' });

      await component.save();

      expect(updateGroup).toHaveBeenCalledOnceWith({ id: 7, name: 'Wohngemeinschaft', dateCreated: wg.dateCreated, currencyCode: 'EUR' });
      expect(addGroup).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
    });

    it('Gruppe verschwindet (gelöscht/entfernt): schließt mit GROUP_GONE_ROLE', () => {
      overview.set([]);
      fixture.detectChanges();

      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_GONE_ROLE, MODAL_ID);
    });

    it('Gruppe verschwindet während des Speicherns: schließt sofort, das spätere Ergebnis schließt nicht noch einmal', async () => {
      const settle = pending(updateGroup);
      component.form.patchValue({ name: 'Neu' });
      const save = component.save();

      overview.set([]);
      fixture.detectChanges();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_GONE_ROLE, MODAL_ID);

      settle(true);
      await save;
      expect(modalCtrl.dismiss).toHaveBeenCalledTimes(1);
    });

    it('andere Gruppen in der Liste ändern nichts', () => {
      overview.set([wg, aGroupOverview()]);
      fixture.detectChanges();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });
  });

  // DIV-77: Währungsauswahl, Bestätigung bei vorhandenen Buchungen, 409
  describe('Währung', () => {
    const select = () => el().querySelector('ion-select.currency-select') as HTMLIonSelectElement;
    const options = () => Array.from(el().querySelectorAll('ion-select.currency-select ion-select-option'));
    const confirmKey = (suffix: string) => `groups.form_sheet.currency_confirm.${suffix}`;
    const conflict = 'confirmation-required';

    it('Anlegen: Auswahl mit allen zwölf Währungen, EUR vorausgewählt; gewählte Währung geht mit, ohne Dialog', async () => {
      create('create');

      expect(select()).not.toBeNull();
      expect(options().map(o => o.getAttribute('ng-reflect-value') ?? (o as HTMLIonSelectOptionElement).value))
        .toEqual(['EUR', 'USD', 'GBP', 'CHF', 'CAD', 'AUD', 'MXN', 'BRL', 'PLN', 'CZK', 'NOK', 'SEK']);
      expect(options()[1].textContent!.replace(/\s+/g, ' ').trim()).toBe('USD – currency.name.USD ($)');
      expect(el().querySelector('.currency-hint')!.textContent!.trim()).toBe('groups.form_sheet.currency_hint');
      expect(component.form.controls.currencyCode.value).toBe('EUR');

      component.form.patchValue({ name: 'Reise', currencyCode: 'USD' });
      await component.save();

      expect(alertCreate).not.toHaveBeenCalled();
      expect(addGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Reise', currencyCode: 'USD' }), undefined);
    });

    it('Bearbeiten: Währung der Gruppe vorausgewählt; nur Namensänderung fragt nicht nach', async () => {
      create('edit', { ...wg, currencyCode: 'CHF', hasBookings: true });
      expect(component.form.controls.currencyCode.value).toBe('CHF');

      component.form.patchValue({ name: 'WG neu' });
      await component.save();

      expect(alertCreate).not.toHaveBeenCalled();
      expect(updateGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'WG neu', currencyCode: 'CHF' }));
      expect(updateGroup.calls.mostRecent().args[0].confirmCurrencyChange).toBeUndefined();
    });

    it('dieser App-Version unbekannte Gruppenwährung: als letzte Option ohne Namen, Umbenennen sendet sie unverändert', async () => {
      create('edit', { ...wg, currencyCode: 'JPY', hasBookings: true });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.form.controls.currencyCode.value).toBe('JPY');
      expect(options().length).toBe(13);
      expect(options()[12].textContent!.replace(/\s+/g, ' ').trim()).toBe('JPY (¥)');

      component.form.patchValue({ name: 'WG neu' });
      await component.save();

      expect(alertCreate).not.toHaveBeenCalled();
      expect(updateGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'WG neu', currencyCode: 'JPY' }));
    });

    it('Wechsel ohne Buchungen: kein Dialog, Request ohne Bestätigung, Sheet schließt', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: false });

      component.form.patchValue({ currencyCode: 'USD' });
      await component.save();

      expect(alertCreate).not.toHaveBeenCalled();
      expect(updateGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ currencyCode: 'USD' }));
      expect(updateGroup.calls.mostRecent().args[0].confirmCurrencyChange).toBeUndefined();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
    });

    [true, undefined].forEach(hasBookings => {
      it(`Wechsel mit Buchungen (hasBookings ${hasBookings}): Dialog, Abbrechen sendet nichts und lässt das Sheet offen (AC7)`, async () => {
        create('edit', { ...wg, currencyCode: 'EUR', hasBookings });
        alertRoles = ['cancel'];

        component.form.patchValue({ currencyCode: 'USD' });
        await component.save();

        expect(alertCreate).toHaveBeenCalledTimes(1);
        expect(updateGroup).not.toHaveBeenCalled();
        expect(modalCtrl.dismiss).not.toHaveBeenCalled();
        expect(saving()).toBeFalse();
        expect(component.form.enabled).toBeTrue();
      });
    });

    it('Wechsel mit Buchungen: Dialog erklärt „keine Umrechnung“, Bestätigen sendet confirmCurrencyChange (AC5, AC6)', async () => {
      const translate = TestBed.inject(TranslateService);
      spyOn(translate, 'instant').and.callThrough();
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: true });
      alertRoles = ['confirm'];

      component.form.patchValue({ currencyCode: 'USD' });
      await component.save();

      const plain = (text: string) => text.replace(/\u00a0|\u202f/g, ' ');
      const params = (translate.instant as jasmine.Spy).calls.all()
        .find(call => call.args[0] === confirmKey('message'))!.args[1] as Record<string, string>;
      expect(params['currency']).toBe('currency.name.USD ($)');
      expect(plain(params['before'])).toBe('100,00 €');
      expect(plain(params['after'])).toBe('100,00 $');
      expect(alertCreate).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        header: confirmKey('header'),
        buttons: [jasmine.objectContaining({ text: confirmKey('cancel'), role: 'cancel' }),
                  jasmine.objectContaining({ text: confirmKey('ok'), role: 'confirm' })],
      }));
      expect(updateGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ currencyCode: 'USD', confirmCurrencyChange: true }));
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
    });

    it('Erfolg: Felder bleiben bis zum Schließen gesperrt (kein kurzes Wieder-Aktivieren, Nutzerhinweis Flackern)', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: true });
      alertRoles = ['confirm'];

      component.form.patchValue({ currencyCode: 'USD' });
      await component.save();

      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
      expect(component.form.disabled).toBeTrue();
      expect(saving()).toBeFalse();
    });

    it('löst vor dem Dialog den Fokus (sonst öffnete die Fokus-Rückgabe kurz die Tastatur)', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: true });
      const input = document.createElement('input');
      el().appendChild(input);
      input.focus();
      let focusedAtPresent: Element | null = null;
      alertCreate.and.callFake(async () => ({
        present: async () => { focusedAtPresent = document.activeElement; },
        onDidDismiss: async () => ({ role: 'cancel' }),
        dismiss: async () => true,
        remove: () => undefined,
      }));

      component.form.patchValue({ currencyCode: 'USD' });
      await component.save();

      expect(focusedAtPresent === input).toBeFalse();
      expect(updateGroup).not.toHaveBeenCalled();
    });

    it('409 (Buchung inzwischen entstanden): Dialog nachträglich, nach Bestätigung zweiter Request mit Bestätigung', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: false });
      updateGroup.and.returnValues(Promise.resolve(conflict), Promise.resolve('ok'));
      alertRoles = ['confirm'];

      component.form.patchValue({ currencyCode: 'BRL' });
      await component.save();

      expect(updateGroup).toHaveBeenCalledTimes(2);
      expect(updateGroup.calls.argsFor(0)[0].confirmCurrencyChange).toBeUndefined();
      expect(updateGroup.calls.argsFor(1)[0]).toEqual(jasmine.objectContaining({ currencyCode: 'BRL', confirmCurrencyChange: true }));
      expect(alertCreate).toHaveBeenCalledTimes(1);
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
    });

    it('409 und Abbrechen im Dialog: kein zweiter Request, Sheet bleibt offen und bedienbar', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: false });
      updateGroup.and.resolveTo(conflict);
      alertRoles = ['cancel'];

      component.form.patchValue({ currencyCode: 'BRL' });
      await component.save();

      expect(updateGroup).toHaveBeenCalledTimes(1);
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(component.form.enabled).toBeTrue();
      expect(saving()).toBeFalse();
    });

    it('Gruppe verschwindet bei offenem Dialog: Dialog schließt mit, kein Request', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: true });
      component.form.patchValue({ currencyCode: 'USD' });

      const save = component.save();
      await new Promise(resolve => setTimeout(resolve));
      expect(alerts.length).toBe(1);

      overview.set([]);
      fixture.detectChanges();
      await save;

      expect(alerts[0].dismiss).toHaveBeenCalled();
      expect(updateGroup).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_GONE_ROLE, MODAL_ID);
    });

    it('zweiter Tipp auf „Speichern“ bei offenem Dialog bewirkt nichts', async () => {
      create('edit', { ...wg, currencyCode: 'EUR', hasBookings: true });
      component.form.patchValue({ currencyCode: 'USD' });

      const first = component.save();
      await new Promise(resolve => setTimeout(resolve));
      await component.save();

      expect(alertCreate).toHaveBeenCalledTimes(1);
      alerts[0].dismiss();
      await first;
      expect(updateGroup).not.toHaveBeenCalled();
    });
  });

  // Schmales Display, langer Name: kein horizontaler Überlauf, Footer mit beiden Buttons sichtbar
  describe('Layout', () => {
    const WIDTH = 320;
    const longName = 'Eine außerordentlich lange Gruppenbezeichnung, die auf kleinen Displays umbrechen muss';

    beforeEach(async () => {
      // Ohne Loader zeigt die Pipe den Schlüssel (unumbrechbar lang); für die Breite zählen die echten Texte
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('de', { groups: { form_sheet: {
        title_edit: 'Gruppe bearbeiten', name_label: 'Gruppenname', btn_cancel: 'Abbrechen', btn_save: 'Speichern',
      } } }, true);
      translate.use('de');
      create('edit', aGroupOverview({ id: 9, name: longName }));
      overview.set([component.group!]);
      const root = el();
      Object.assign(root.style, { position: 'fixed', bottom: '0', left: '0', width: `${WIDTH}px`, display: 'block' });
      fixture.detectChanges();
      await ionicReady(root);
      await new Promise(resolve => requestAnimationFrame(resolve));
    });

    it('kein horizontaler Überlauf; Footer wie der Ausgabenfilter: auf schmalem Display „Speichern“ über „Abbrechen“', () => {
      const root = el();
      const cancel = button('btn-cancel').getBoundingClientRect();
      const save = button('btn-save').getBoundingClientRect();

      expect(root.scrollWidth).toBeLessThanOrEqual(WIDTH);
      expect(el().querySelector('.sheet-subtitle')!.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
      expect(save.right).toBeLessThanOrEqual(WIDTH + 0.5);
      expect(cancel.right).toBeLessThanOrEqual(WIDTH + 0.5);
      // wrap-reverse wie im Filter-Sheet: die Hauptaktion steht oben
      expect(save.bottom).toBeLessThanOrEqual(cancel.top + 0.5);
      expect(button('btn-cancel').getAttribute('fill')).toBe('clear');
    });

    // Codex F-004: Android-Schriftgröße als Root-Schrift (am Dokument), Wirkung per getComputedStyle nachgewiesen
    describe('200 % Schrift', () => {
      afterEach(() => document.documentElement.style.fontSize = '');

      it('Kopf, Feld und beide Buttons bleiben ohne horizontalen Überlauf erreichbar', async () => {
        const title = el().querySelector('h2') as HTMLElement;
        const before = parseFloat(getComputedStyle(title).fontSize);
        document.documentElement.style.fontSize = '200%';
        fixture.detectChanges();
        await new Promise(resolve => requestAnimationFrame(resolve));
        await new Promise(resolve => requestAnimationFrame(resolve));

        expect(parseFloat(getComputedStyle(title).fontSize)).toBeGreaterThanOrEqual(before * 1.9);
        expect(el().scrollWidth).toBeLessThanOrEqual(WIDTH);
        for (const cls of ['btn-cancel', 'btn-save', 'btn-close']) {
          const r = button(cls).getBoundingClientRect();
          expect(r.width).withContext(cls).toBeGreaterThan(0);
          expect(r.right).withContext(cls).toBeLessThanOrEqual(WIDTH + 0.5);
        }
        expect(el().querySelector('.group-name-input')!.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
      });

      // Codex-Implementierungsreview F-001: Der Kopf schrumpft nicht; ein langer Name im Untertitel darf das Feld nicht
      // aus dem höhenbegrenzten Sheet (90vh) verdrängen. Höhe wie ein 640 px hohes Handy über das Karma-iframe.
      it('Gruppenname mit 200 Zeichen: Untertitel höchstens zwei Zeilen, Namensfeld und Buttons vollständig sichtbar', async () => {
        const frame = window.frameElement as HTMLElement | null;
        const frameHeight = frame?.style.height ?? '';
        try {
          if (frame) frame.style.height = '711px'; // 90vh = 640 px
          const longest = aGroupOverview({ id: 11, name: 'Wohngemeinschaft '.repeat(12).slice(0, 200) });
          overview.set([longest]);
          create('edit', longest);
          Object.assign(el().style, { position: 'fixed', bottom: '0', left: '0', width: `${WIDTH}px`, display: 'block' });
          document.documentElement.style.fontSize = '200%';
          fixture.detectChanges();
          await ionicReady(el());
          await new Promise(resolve => requestAnimationFrame(resolve));
          await new Promise(resolve => requestAnimationFrame(resolve));

          const sheet = el().querySelector('.group-form-sheet')!.getBoundingClientRect();
          const subtitle = el().querySelector('.sheet-subtitle') as HTMLElement;
          const lineHeight = parseFloat(getComputedStyle(subtitle).fontSize) * 1.5;
          expect(subtitle.getBoundingClientRect().height).toBeLessThanOrEqual(lineHeight * 2 + 1);
          expect(subtitle.textContent!.trim()).toBe(longest.name.trim());
          const field = el().querySelector('ion-item.group-form-field')!.getBoundingClientRect();
          const body = el().querySelector('.sheet-body')!.getBoundingClientRect();
          expect(field.height).toBeGreaterThan(0);
          expect(field.bottom).toBeLessThanOrEqual(body.bottom + 0.5);
          expect(field.top).toBeGreaterThanOrEqual(body.top - 0.5);
          for (const cls of ['btn-cancel', 'btn-save']) {
            const r = button(cls).getBoundingClientRect();
            expect(r.bottom).withContext(cls).toBeLessThanOrEqual(sheet.bottom + 0.5);
            expect(r.top).withContext(cls).toBeGreaterThanOrEqual(sheet.top - 0.5);
          }
        } finally {
          if (frame) frame.style.height = frameHeight;
        }
      });
    });
  });
});
