import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { IonicModule, ModalController } from '@ionic/angular';
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
  let overview: WritableSignal<GroupOverview[]>;
  let saving: WritableSignal<boolean>;

  const MODAL_ID = 'group-form-sheet-42';
  const wg = aGroupOverview({ id: 7, name: 'WG', dateCreated: new Date('2024-01-01T00:00:00Z') });
  const el = (): HTMLElement => fixture.nativeElement;
  const button = (cls: string) => el().querySelector(`ion-button.${cls}`) as HTMLIonButtonElement;

  /** Steuerbares Ergebnis des Service-Aufrufs. */
  const pending = (spy: jasmine.Spy) => {
    let settle!: (ok: boolean) => void;
    spy.and.returnValue(new Promise<boolean>(resolve => settle = resolve));
    return (ok: boolean) => settle(ok);
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
    updateGroup = jasmine.createSpy('updateGroup').and.resolveTo(true);
    overview = signal([wg]);
    saving = signal(false);
    TestBed.configureTestingModule({
      declarations: [GroupFormSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ModalController, useValue: modalCtrl },
        { provide: GroupService, useValue: { groupOverviewList: overview, addGroup, updateGroup } },
      ],
    });
  });

  describe('Gruppe hinzufügen', () => {
    beforeEach(() => create('create'));

    it('zeigt Titel „Gruppe hinzufügen“, leeres Feld und „Erstellen“', () => {
      expect(el().querySelector('h2')!.textContent!.trim()).toBe('groups.form_sheet.title_create');
      expect(el().querySelector('.sheet-subtitle')).toBeNull();
      expect(component.form.getRawValue()).toEqual({ name: '' });
      expect(button('btn-save').textContent!.trim()).toBe('groups.form_sheet.btn_create');
    });

    it('speichert den getrimmten Namen über addGroup und schließt nach Erfolg mit Rolle saved', async () => {
      component.form.setValue({ name: '  Verein ' });

      await component.save();

      expect(addGroup).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Verein' }));
      expect(updateGroup).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_SHEET_SAVED_ROLE, MODAL_ID);
      expect(saving()).toBeFalse();
    });

    it('leerer Name: kein Request, Feldfehler sichtbar', async () => {
      component.form.setValue({ name: '   ' });

      await component.save();
      fixture.detectChanges();

      expect(addGroup).not.toHaveBeenCalled();
      expect(el().querySelector('.error-name')!.textContent!.trim()).toBe('groups.form_sheet.name_required');
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    it('Fehler: Sheet bleibt mit der Eingabe offen und ist wieder bedienbar', async () => {
      addGroup.and.resolveTo(false);
      component.form.setValue({ name: 'Verein' });

      await component.save();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(component.form.getRawValue()).toEqual({ name: 'Verein' });
      expect(component.form.enabled).toBeTrue();
      expect(saving()).toBeFalse();
    });

    it('sperrt während des Speicherns einen zweiten Versuch (Doppel-Tipp, Enter)', async () => {
      const settle = pending(addGroup);
      component.form.setValue({ name: 'Verein' });

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
      expect(component.form.getRawValue()).toEqual({ name: 'WG' });
      expect(button('btn-save').textContent!.trim()).toBe('groups.form_sheet.btn_save');
    });

    it('speichert über updateGroup mit ID und Gründungsdatum der Gruppe', async () => {
      component.form.setValue({ name: ' Wohngemeinschaft ' });

      await component.save();

      expect(updateGroup).toHaveBeenCalledOnceWith({ id: 7, name: 'Wohngemeinschaft', dateCreated: wg.dateCreated });
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
      component.form.setValue({ name: 'Neu' });
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
    });
  });
});
