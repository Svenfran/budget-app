import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { provideServiceTesting } from 'src/testing/test-providers';
import { AddShoppingItemsDto } from '../model/add-shopping-items-dto';
import { ShoppingSuggestionsDto } from '../model/shopping-suggestions-dto';
import { ShoppinglistService } from '../service/shoppinglist.service';
import { SuggestionsSheetComponent } from './suggestions-sheet.component';

describe('SuggestionsSheetComponent (DIV-69)', () => {
  let fixture: ComponentFixture<SuggestionsSheetComponent>;
  let component: SuggestionsSheetComponent;
  let adding: WritableSignal<boolean>;
  let kontextGueltig: boolean;
  let addItems: jasmine.Spy<(dto: AddShoppingItemsDto) => Promise<boolean>>;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let addingWhenDismissed: boolean | undefined;

  const MODAL_ID = 'suggestions-sheet-42';
  /** dismiss gezielt für das eigene Modal (Review F-001), nicht für das oberste. */
  const eigenesModal = [undefined, undefined, MODAL_ID] as const;
  const key = (suffix: string) => `alerts.shoppinglist.suggestions.${suffix}`;
  const vorschlaege = (names: string[], defaultSuggestions = false): ShoppingSuggestionsDto =>
    ({ groupId: 7, shoppingListId: 3, defaultSuggestions, names });

  const el = (): HTMLElement => fixture.nativeElement;
  const checkboxes = () => Array.from(el().querySelectorAll('ion-checkbox')) as HTMLIonCheckboxElement[];
  const labels = () => Array.from(el().querySelectorAll('ion-item ion-label')) as HTMLElement[];
  const addButton = () => el().querySelector('ion-button.btn-add') as HTMLIonButtonElement | null;
  const toggleAllButton = () => el().querySelector('ion-button.btn-toggle-all') as HTMLIonButtonElement;
  const closeButton = () => el().querySelector('ion-button.btn-close') as HTMLIonButtonElement;
  const zaehler = () => el().querySelector('.selection-count')?.textContent?.trim();
  const buttonText = () => addButton()?.textContent?.trim();

  /** ionChange so, wie die Checkbox es meldet; danach Change Detection. */
  const ionChange = (index: number, checked: boolean) => {
    checkboxes()[index].dispatchEvent(new CustomEvent('ionChange', { detail: { checked, value: 'on' } }));
    fixture.detectChanges();
  };
  const klickAufName = (index: number) => {
    labels()[index].click();
    fixture.detectChanges();
  };

  const render = (suggestions = vorschlaege(['Milch', 'Brot', 'Eier'])) => {
    component.listName = 'Edeka';
    component.suggestions = suggestions;
    component.groupId = 7;
    component.listId = 3;
    component.inContext = () => kontextGueltig;
    component.adding = adding;
    component.modalId = MODAL_ID;
    fixture.detectChanges();
  };

  beforeEach(() => {
    addItems = jasmine.createSpy('addItemsToShoppingList');
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    addingWhenDismissed = undefined;
    modalCtrl.dismiss.and.callFake(async () => {
      addingWhenDismissed = adding();
      return true;
    });
    TestBed.configureTestingModule({
      declarations: [SuggestionsSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ShoppinglistService, useValue: { addItemsToShoppingList: addItems } },
        { provide: ModalController, useValue: modalCtrl },
      ],
    });
    // Nur die Texte mit Parametern übersetzen, damit Zähler und Button-Beschriftung samt Interpolation prüfbar sind
    // (der i18n-Konsistenztest prüft nur gleiche Schlüssel); alle übrigen Texte bleiben als Key sichtbar.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', { alerts: { shoppinglist: { suggestions: {
      selected_count: '{{count}} von {{total}}', add_one: 'eins:{{count}}', add_other: 'viele:{{count}}',
    } } } });
    translate.use('de');
    fixture = TestBed.createComponent(SuggestionsSheetComponent);
    component = fixture.componentInstance;
    adding = signal(false);
    kontextGueltig = true;
  });

  it('zeigt Titel, Listenname mit Herkunft der Vorschläge und einen beschrifteten Schließen-Button (AC 2)', () => {
    render();

    expect(el().querySelector('h2')?.textContent).toContain(key('header'));
    expect(el().querySelector('.sheet-subtitle')?.textContent).toContain('Edeka');
    expect(el().querySelector('.sheet-subtitle')?.textContent).toContain(key('subtitle_history'));
    expect(closeButton().getAttribute('aria-label')).toBe(key('close'));
  });

  it('Standardvorschläge: Untertitel nennt allgemeine Vorschläge', () => {
    render(vorschlaege(['Milch'], true));

    expect(el().querySelector('.sheet-subtitle')?.textContent).toContain(key('subtitle_defaults'));
    expect(el().querySelector('.sheet-subtitle')?.textContent).not.toContain(key('subtitle_history'));
  });

  // wie DIV-68 bei ion-radio: ion-checkbox blendet seinen Label-Bereich aus, wenn es beim Rendern noch keinen Text
  // enthält – Text deshalb im ion-label daneben, die Checkbox selbst bleibt leer und ist per aria-label beschriftet
  it('je Vorschlag eine leere, nicht angehakte Checkbox und der Name im ion-label, in Vorschlagsreihenfolge (AC 2)', () => {
    render();

    const names = Array.from(el().querySelectorAll('ion-label .suggestion-name')).map(span => span.textContent?.trim());
    expect(names).toEqual(['Milch', 'Brot', 'Eier']);
    checkboxes().forEach((checkbox, i) => {
      expect(checkbox.textContent?.trim()).toBe('');
      expect(checkbox.getAttribute('aria-label')).toBe(['Milch', 'Brot', 'Eier'][i]);
      expect(checkbox.checked).toBeFalse();
    });
    expect(zaehler()).toBe('0 von 3');
  });

  it('ohne Auswahl ist der Button deaktiviert und hinzufügen sendet nichts (AC 5)', async () => {
    render();

    expect(addButton()!.disabled).toBeTrue();
    expect(buttonText()).toContain(key('add_none'));

    await component.add();

    expect(addItems).not.toHaveBeenCalled();
    expect(modalCtrl.dismiss).not.toHaveBeenCalled();
  });

  describe('Auswahl (F-002: Checkbox setzt, Name toggelt)', () => {
    beforeEach(() => render());

    it('ionChange der Checkbox setzt und entfernt die Auswahl; Zähler und Button folgen sofort (AC 3, 4)', () => {
      ionChange(0, true);
      expect(component.isSelected('Milch')).toBeTrue();
      expect(checkboxes()[0].checked).toBeTrue();
      expect(zaehler()).toBe('1 von 3');
      expect(buttonText()).toContain('eins:1');
      expect(addButton()!.disabled).toBeFalse();

      ionChange(2, true);
      expect(zaehler()).toBe('2 von 3');
      expect(buttonText()).toContain('viele:2');

      ionChange(0, false);
      expect(component.isSelected('Milch')).toBeFalse();
      expect(checkboxes()[0].checked).toBeFalse();
      expect(zaehler()).toBe('1 von 3');
      expect(buttonText()).toContain('eins:1');
    });

    it('dasselbe ionChange zweimal ändert nichts zusätzlich', () => {
      ionChange(1, true);
      ionChange(1, true);

      expect(component.selectedCount()).toBe(1);
      expect(component.isSelected('Brot')).toBeTrue();
    });

    it('Klick auf den Namen toggelt genau einmal', () => {
      klickAufName(1);
      expect(component.isSelected('Brot')).toBeTrue();
      expect(checkboxes()[1].checked).toBeTrue();
      expect(component.selectedCount()).toBe(1);

      klickAufName(1);
      expect(component.isSelected('Brot')).toBeFalse();
      expect(component.selectedCount()).toBe(0);
    });

    // Der Namens-Handler hängt am ion-label; liegt das Klickziel doch in der Checkbox, gehört der Klick ihr (ionChange).
    it('ein Klick mit Ziel in der Checkbox toggelt nicht zusätzlich über den Namens-Handler', () => {
      component.onNameClick({ target: checkboxes()[0] } as unknown as Event, 'Milch');

      expect(component.selectedCount()).toBe(0);
    });

    it('„Alle auswählen“ wählt alle, danach „Auswahl aufheben“ keine mehr', () => {
      expect(toggleAllButton().textContent).toContain(key('select_all'));

      toggleAllButton().click();
      fixture.detectChanges();
      expect(checkboxes().every(checkbox => checkbox.checked)).toBeTrue();
      expect(zaehler()).toBe('3 von 3');
      expect(toggleAllButton().textContent).toContain(key('deselect_all'));

      toggleAllButton().click();
      fixture.detectChanges();
      expect(component.selectedCount()).toBe(0);
      expect(toggleAllButton().textContent).toContain(key('select_all'));
    });

    it('„Auswahl aufheben“ erscheint erst, wenn alle ausgewählt sind', () => {
      ionChange(0, true);
      ionChange(1, true);
      expect(toggleAllButton().textContent).toContain(key('select_all'));

      ionChange(2, true);
      expect(toggleAllButton().textContent).toContain(key('deselect_all'));
    });
  });

  describe('Hinzufügen', () => {
    beforeEach(() => render());

    it('sendet nur die ausgewählten Namen in Vorschlagsreihenfolge, nicht in Klickreihenfolge (AC 6)', () => {
      addItems.and.returnValue(new Promise(() => undefined));
      klickAufName(2); // Eier
      klickAufName(0); // Milch

      addButton()!.click();

      expect(addItems).toHaveBeenCalledOnceWith({ groupId: 7, shoppingListId: 3, names: ['Milch', 'Eier'] });
    });

    it('nach Erfolg zuerst adding zurücksetzen, dann schließen (AC 8)', async () => {
      addItems.and.resolveTo(true);
      ionChange(0, true);

      await component.add();

      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(...eigenesModal);
      expect(addingWhenDismissed).toBeFalse();
      expect(adding()).toBeFalse();
    });

    it('bei Fehler bleibt das Sheet mit der Auswahl offen und wieder bedienbar (AC 9)', async () => {
      addItems.and.resolveTo(false);
      ionChange(0, true);
      ionChange(2, true);

      await component.add();
      fixture.detectChanges();

      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
      expect(adding()).toBeFalse();
      expect(component.isSelected('Milch')).toBeTrue();
      expect(component.isSelected('Eier')).toBeTrue();
      expect(checkboxes()[0].checked).toBeTrue();
      expect(addButton()!.disabled).toBeFalse();
    });

    it('während des Hinzufügens: kein zweiter Request, Auswahl und Buttons gesperrt, Spinner sichtbar', () => {
      addItems.and.returnValue(new Promise(() => undefined));
      ionChange(0, true);

      component.add();
      component.add();
      fixture.detectChanges();

      expect(addItems).toHaveBeenCalledTimes(1);
      expect(adding()).toBeTrue();
      expect(addButton()!.disabled).toBeTrue();
      expect(closeButton().disabled).toBeTrue();
      expect(toggleAllButton().disabled).toBeTrue();
      checkboxes().forEach(checkbox => expect(checkbox.disabled).toBeTrue());
      expect(el().querySelector('ion-button.btn-add ion-spinner')).not.toBeNull();

      ionChange(1, true);
      ionChange(0, false);
      klickAufName(2);
      component.toggleAll();

      expect(component.isSelected('Milch')).toBeTrue();
      expect(component.selectedCount()).toBe(1);
    });

    it('Kontext inzwischen weg (Gruppe, Sitzung oder Liste): kein Request, Sheet schließt', async () => {
      ionChange(0, true);
      kontextGueltig = false;

      await component.add();

      expect(addItems).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(...eigenesModal);
    });

    // Review F-001: ohne ID schließt Ionic das oberste Modal. Ist inzwischen ein zweites Sheet darüber geöffnet,
    // darf der Erfolg dieses Sheets nur dieses schließen, nicht das andere samt seiner Auswahl.
    it('zweites Sheet darüber geöffnet: Erfolg schließt nur das eigene Sheet', async () => {
      const offen = [MODAL_ID, 'suggestions-sheet-43']; // 43 liegt oben
      modalCtrl.dismiss.and.callFake(async (_data?: unknown, _role?: string, id?: string) => {
        const ziel = id ?? offen[offen.length - 1];
        offen.splice(offen.indexOf(ziel), 1);
        return true;
      });
      addItems.and.resolveTo(true);
      ionChange(0, true);

      await component.add();

      expect(offen).toEqual(['suggestions-sheet-43']);
    });
  });

  it('Schließen-Button schließt ohne Request', () => {
    render();

    closeButton().click();

    expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(...eigenesModal);
    expect(addItems).not.toHaveBeenCalled();
  });

  it('keine Vorschläge: Leerzustand, keine Liste, kein Zähler und kein Hinzufügen-Button', () => {
    render(vorschlaege([]));

    expect(el().querySelector('.empty-state')?.textContent).toContain(key('empty'));
    expect(checkboxes().length).toBe(0);
    expect(el().querySelector('.selection-bar')).toBeNull();
    expect(addButton()).toBeNull();
    expect(closeButton()).not.toBeNull();
  });
});
