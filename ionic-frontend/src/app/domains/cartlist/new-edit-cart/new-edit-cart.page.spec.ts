import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { IonicModule, LoadingController, MenuController } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from 'src/app/auth/auth.service';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { CategoryDto } from 'src/app/model/category-dto';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { LanguageService } from 'src/app/service/language.service';
import { aCart, aCategory, aGroup, aLoggedInUser, aUser, REFERENCE_DATE } from 'src/testing/test-data';
import { authServiceStub, provideServiceTesting } from 'src/testing/test-providers';
import { Cart } from '../model/cart';
import { CartService } from '../service/cart.service';
import { NewEditCartPage } from './new-edit-cart.page';

/**
 * DIV-73: Formular „Einkauf hinzufügen“ im Duplizierungsmodus und die gemeinsamen Speicherregeln (Kategorien der
 * eigenen Anfrage, Sperre gegen doppeltes Speichern). Kategorien-Laden und LoadingController sind manuell steuerbar.
 */
describe('NewEditCartPage', () => {
  const GROUP_ID = 5;
  const lebensmittel = aCategory({ id: 42, name: 'Lebensmittel', groupId: GROUP_ID });
  const drogerie = aCategory({ id: 43, name: 'Drogerie', groupId: GROUP_ID });
  const anna = aUser({ userName: 'Anna' });
  const vorlage = aCart({
    id: 1, title: 'Pizza', amount: 12.5, description: 'Freitag', groupId: GROUP_ID, userDto: anna,
    categoryDto: lebensmittel, recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateId: 7,
    datePurchased: new Date('2025-05-01T00:00:00Z'),
  });
  const fremde = aCart({ id: 2, groupId: GROUP_ID, userDto: aUser({ userName: 'Ben' }), categoryDto: lebensmittel });

  let fixture: ComponentFixture<NewEditCartPage>;
  let page: NewEditCartPage;
  let categoryCallbacks: ((result: CategoryDto[] | null) => void)[];
  let sharedCategories: ReturnType<typeof signal<CategoryDto[]>>;
  let cartService: { cartList: ReturnType<typeof signal<Cart[]>>; addCart: jasmine.Spy; updateCart: jasmine.Spy };
  let loadingCreate: jasmine.Spy;
  let pendingLoadings: ((el: unknown) => void)[];
  let navigate: jasmine.Spy;

  function createPage(route: { id?: string; duplicateOf?: string } = {}, carts: Cart[] = [vorlage, fremde]) {
    categoryCallbacks = [];
    pendingLoadings = [];
    sharedCategories = signal<CategoryDto[]>([]);
    cartService = {
      cartList: signal<Cart[]>(carts),
      addCart: jasmine.createSpy('addCart'),
      updateCart: jasmine.createSpy('updateCart'),
    };
    loadingCreate = jasmine.createSpy('create').and.callFake(() => new Promise(resolve => pendingLoadings.push(resolve)));
    const params = route.id ? { id: route.id } : {};
    const queryParams = route.duplicateOf ? { duplicateOf: route.duplicateOf } : {};

    TestBed.configureTestingModule({
      declarations: [NewEditCartPage],
      imports: [IonicModule.forRoot(), ReactiveFormsModule, TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: {
          paramMap: convertToParamMap(params), queryParamMap: convertToParamMap(queryParams) } } },
        { provide: CartService, useValue: cartService },
        { provide: CategoryService, useValue: {
          categories: sharedCategories,
          getCategoriesByGroup: (_group: unknown, onComplete: (result: CategoryDto[] | null) => void) =>
            categoryCallbacks.push(onComplete),
        } },
        { provide: GroupService, useValue: {
          activeGroup: signal(aGroup({ id: GROUP_ID, dateCreated: new Date('2024-01-01T00:00:00Z') })),
          groupMembershipHistory: signal([]),
          getGroupMembershipHistoryForGroupAndUser: jasmine.createSpy('getGroupMembershipHistoryForGroupAndUser'),
        } },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ id: 9, name: 'Anna' })) },
        { provide: LoadingController, useValue: { create: loadingCreate } },
        { provide: MenuController, useValue: { enable: jasmine.createSpy('enable').and.resolveTo(true) } },
        { provide: LanguageService, useValue: { currentLang: signal('de') } },
      ],
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(NewEditCartPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    page.ionViewWillEnter();
  }

  /** Antwort auf die Kategorien-Anfrage der Seite (`null` = Ladefehler). */
  function answerCategories(result: CategoryDto[] | null) {
    expect(categoryCallbacks.length).withContext('genau eine Kategorien-Anfrage').toBe(1);
    categoryCallbacks[0](result);
    fixture.detectChanges();
  }

  /** Löst alle offenen `loadingCtrl.create()`-Promises auf und wartet die `.then`-Callbacks ab. */
  async function resolveLoadings() {
    pendingLoadings.splice(0).forEach(resolve => resolve({ present: jasmine.createSpy(), dismiss: jasmine.createSpy() }));
    for (let i = 0; i < 5; i++) await Promise.resolve();
    fixture.detectChanges();
  }

  const checkmark = () => fixture.nativeElement.querySelector('ion-header ion-button') as HTMLElement & { disabled: boolean };
  // Text am <small>: Ionic (Stencil) liefert für ion-label selbst kein textContent der geslotteten Inhalte
  const errorTexts = () => Array.from(fixture.nativeElement.querySelectorAll('ion-label[color="danger"] small'))
    .map(el => (el as HTMLElement).textContent!.trim());
  const optionTexts = () => Array.from(
    fixture.nativeElement.querySelectorAll('ion-select[formcontrolname="categoryId"] ion-select-option'))
    .map(el => (el as HTMLElement).textContent!.trim());

  beforeEach(() => jasmine.clock().install().mockDate(REFERENCE_DATE));
  afterEach(() => jasmine.clock().uninstall());

  describe('Duplizieren', () => {
    it('belegt Titel, Betrag, Beschreibung und Kategorie vor, Datum heute, keine Wiederholung, mit Hinweis (AC 2–5)', () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);

      expect(page.isAddMode).toBeTrue();
      expect(page.isDuplicate).toBeTrue();
      expect(page.form.value).toEqual(jasmine.objectContaining({
        id: '', title: 'Pizza', amount: '12.50', description: 'Freitag', categoryId: 42,
        datePurchased: '15.06.2025', recurrenceType: RECURRENCE_TYPE.NONE, templateUpdateSelected: false,
      }));
      expect(fixture.nativeElement.querySelector('ion-title').textContent).toContain('carts.title_add');
      expect(fixture.nativeElement.querySelector('.duplicate-hint').textContent).toContain('carts.duplicate_hint');
    });

    it('legt vor dem Speichern nichts an (AC 7)', () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);

      expect(cartService.addCart).not.toHaveBeenCalled();
      expect(loadingCreate).not.toHaveBeenCalled();
    });

    it('Abbrechen (Seite ohne Speichern verlassen) legt nichts an und ändert die Liste nicht (AC 10)', () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);
      page.form.patchValue({ title: 'Geändert' });

      page.ionViewWillLeave();
      fixture.destroy();

      expect(cartService.addCart).not.toHaveBeenCalled();
      expect(cartService.cartList()).toEqual([vorlage, fremde]);
    });

    it('Speichern legt genau einen neuen Einkauf ohne ID mit den geänderten Werten an und navigiert zur Liste (AC 6, 8)', async () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);
      page.form.patchValue({ title: 'Pizza groß ', amount: '15.00', categoryId: 43 });

      page.onSubmit();
      await resolveLoadings();

      expect(cartService.addCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        id: null, title: 'Pizza groß', amount: '15.00', description: 'Freitag', groupId: GROUP_ID,
        categoryDto: { id: 43, name: 'Drogerie', groupId: GROUP_ID }, recurrenceType: RECURRENCE_TYPE.NONE,
      }));
      const sent: Cart = cartService.addCart.calls.mostRecent().args[0];
      expect(sent.templateId).toBeUndefined();
      expect(sent.hasActiveTemplate).toBeUndefined();
      expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist']);
    });

    it('zwei Submits vor dem Auflösen des Loading-Promises speichern genau einmal (FA4)', async () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);

      page.onSubmit();
      fixture.detectChanges();
      expect(checkmark().disabled).toBeTrue();
      page.onSubmit();
      await resolveLoadings();

      expect(loadingCreate).toHaveBeenCalledTimes(1);
      expect(cartService.addCart).toHaveBeenCalledTimes(1);
    });

    it('fremde Ausgabe als Vorlage: leere Neuanlage ohne Hinweis', () => {
      createPage({ duplicateOf: '2' });
      answerCategories([lebensmittel, drogerie]);

      expect(page.isDuplicate).toBeFalse();
      expect(page.form.value.title).toBe('');
      expect(fixture.nativeElement.querySelector('.duplicate-hint')).toBeNull();
    });
  });

  describe('Kategorien der eigenen Anfrage (AC 3, FA5)', () => {
    it('gültige Vorlagenkategorie bleibt erhalten, solange nur das gemeinsame Signal leer oder gruppenfremd ist', () => {
      createPage({ duplicateOf: '1' });
      sharedCategories.set([aCategory({ id: 99, name: 'Fremd', groupId: 1 })]);
      fixture.detectChanges();

      expect(page.form.value.categoryId).toBe(42);

      answerCategories([lebensmittel, drogerie]);

      expect(page.form.value.categoryId).toBe(42);
      expect(errorTexts()).toEqual([]);
    });

    it('eine nicht (mehr) vorhandene Kategorie wird erst nach dem Laden geleert, die Pflichtmeldung ist sichtbar', () => {
      createPage({ duplicateOf: '1' });

      expect(page.form.value.categoryId).toBe(42);

      answerCategories([drogerie]);

      expect(page.form.value.categoryId).toBe('');
      expect(page.categoryId!.touched).toBeTrue();
      expect(errorTexts()).toEqual(['global.errors.required']);
    });

    it('früher Submit vor dem Laden: Häkchen gesperrt, kein Request, Vorbelegung bleibt; danach speichert er einmal', async () => {
      createPage({ duplicateOf: '1' });
      sharedCategories.set([]);
      fixture.detectChanges();

      expect(checkmark().disabled).toBeTrue();
      page.onSubmit();
      await resolveLoadings();
      expect(loadingCreate).not.toHaveBeenCalled();
      expect(page.form.value.categoryId).toBe(42);
      expect(page.saving).toBeFalse();

      answerCategories([lebensmittel, drogerie]);
      expect(checkmark().disabled).toBeFalse();
      page.onSubmit();
      await resolveLoadings();

      expect(cartService.addCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        categoryDto: { id: 42, name: 'Lebensmittel', groupId: GROUP_ID } }));
    });

    it('Submit nach einem Ladefehler: Häkchen gesperrt, kein Request, Vorbelegung bleibt', async () => {
      createPage({ duplicateOf: '1' });
      answerCategories(null);

      expect(checkmark().disabled).toBeTrue();
      page.onSubmit();
      await resolveLoadings();

      expect(loadingCreate).not.toHaveBeenCalled();
      expect(page.form.value.categoryId).toBe(42);
    });

    it('verspätete fremde Antwort auf das gemeinsame Signal ändert weder Auswahl noch Vorbelegung', async () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, drogerie]);

      sharedCategories.set([aCategory({ id: 99, name: 'Fremd', groupId: 1 })]);
      fixture.detectChanges();
      sharedCategories.set([]);
      fixture.detectChanges();

      expect(optionTexts()).toEqual(['Lebensmittel', 'Drogerie']);
      expect(page.form.value.categoryId).toBe(42);
      page.onSubmit();
      await resolveLoadings();
      expect(cartService.addCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        categoryDto: { id: 42, name: 'Lebensmittel', groupId: GROUP_ID } }));
    });

    it('ungültige Kategorie beim Speichern: kein Request, keine Sperre; nach Korrektur genau ein Speichern', async () => {
      createPage();
      answerCategories([lebensmittel, drogerie]);
      page.form.patchValue({ title: 'Einkauf', amount: '10', categoryId: 999 });

      page.onSubmit();
      await resolveLoadings();
      expect(loadingCreate).not.toHaveBeenCalled();
      expect(page.form.value.categoryId).toBe('');
      expect(page.saving).toBeFalse();

      page.form.patchValue({ categoryId: 43 });
      page.onSubmit();
      await resolveLoadings();

      expect(cartService.addCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        categoryDto: { id: 43, name: 'Drogerie', groupId: GROUP_ID } }));
    });
  });

  describe('Bestand (AC 13)', () => {
    it('ohne duplicateOf: leeres Formular wie bisher, ohne Hinweis', () => {
      createPage();
      answerCategories([lebensmittel, drogerie]);

      expect(page.isDuplicate).toBeFalse();
      expect(page.form.value).toEqual(jasmine.objectContaining({
        title: '', amount: '', description: '', categoryId: '', datePurchased: '15.06.2025',
        recurrenceType: RECURRENCE_TYPE.NONE,
      }));
      expect(fixture.nativeElement.querySelector('.duplicate-hint')).toBeNull();
    });

    it('Bearbeiten: Vorbelegung wie bisher, Speichern ruft genau einmal updateCart', async () => {
      createPage({ id: '1' });
      answerCategories([lebensmittel, drogerie]);

      expect(page.isAddMode).toBeFalse();
      expect(page.isDuplicate).toBeFalse();
      expect(page.form.value).toEqual(jasmine.objectContaining({
        id: 1, title: 'Pizza', amount: '12.50', categoryId: 42, datePurchased: '01.05.2025',
        recurrenceType: RECURRENCE_TYPE.MONTHLY,
      }));

      page.form.patchValue({ title: 'Pizza neu' });
      page.onSubmit();
      page.onSubmit();
      await resolveLoadings();

      expect(cartService.addCart).not.toHaveBeenCalled();
      expect(cartService.updateCart).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        id: 1, title: 'Pizza neu', groupId: GROUP_ID, categoryDto: { id: 42, name: 'Lebensmittel', groupId: GROUP_ID },
        recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateUpdateSelected: false,
      }));
      expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist']);
    });
  });

  describe('Ausgleichszahlungs-Kategorie (DIV-73 Phase 4, F3; DIV-74: per Kennzeichen, auch beim Bearbeiten gesperrt)', () => {
    const ausgleich = aCategory({ id: 44, name: 'Ausgleichszahlung', groupId: GROUP_ID, settlement: true });
    const ausgleichszahlung = aCart({ id: 3, title: 'Ausgleich an Ben', amount: 20, groupId: GROUP_ID, userDto: anna,
      categoryDto: ausgleich, datePurchased: new Date('2025-05-01T00:00:00Z'), settlementId: 70 });
    const umbenannt = aCategory({ id: 45, name: 'Rückzahlungen', groupId: GROUP_ID, settlement: true });
    const namensgleich = aCategory({ id: 46, name: 'Ausgleichszahlung', groupId: GROUP_ID });

    it('Neuanlage bietet die Ausgleichszahlungs-Kategorie nicht an', () => {
      createPage();
      answerCategories([lebensmittel, ausgleich, drogerie]);

      expect(optionTexts()).toEqual(['Lebensmittel', 'Drogerie']);
    });

    it('Duplizieren bietet die Ausgleichszahlungs-Kategorie nicht an', () => {
      createPage({ duplicateOf: '1' });
      answerCategories([lebensmittel, ausgleich, drogerie]);

      expect(optionTexts()).toEqual(['Lebensmittel', 'Drogerie']);
      expect(page.form.value.categoryId).toBe(42);
    });

    it('Neuanlage mit der Ausgleichszahlungs-Kategorie (z. B. manipuliert) wird ohne Request abgelehnt', async () => {
      createPage();
      answerCategories([lebensmittel, ausgleich, drogerie]);
      page.form.patchValue({ title: 'Einkauf', amount: '10', categoryId: 44 });

      page.onSubmit();
      await resolveLoadings();

      expect(loadingCreate).not.toHaveBeenCalled();
      expect(cartService.addCart).not.toHaveBeenCalled();
      expect(page.form.value.categoryId).toBe('');
    });

    it('Bearbeiten eines normalen Einkaufs bietet die Settlement-Kategorie ebenfalls nicht an (DIV-74)', () => {
      createPage({ id: '1' });
      answerCategories([lebensmittel, ausgleich, drogerie]);

      expect(page.isAddMode).toBeFalse();
      expect(optionTexts()).toEqual(['Lebensmittel', 'Drogerie']);
    });

    it('erkennt die Settlement-Kategorie am Kennzeichen, nicht am Namen (DIV-74)', () => {
      createPage();
      answerCategories([lebensmittel, umbenannt, namensgleich]);

      expect(optionTexts()).toEqual(['Lebensmittel', 'Ausgleichszahlung']);
    });

    it('Bearbeiten einer Ausgleichszahlung über die URL führt zurück zur Liste, ohne Kategorien zu laden (DIV-74)', () => {
      createPage({ id: '3' }, [ausgleichszahlung]);

      expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist']);
      expect(categoryCallbacks.length).toBe(0);
      expect(cartService.updateCart).not.toHaveBeenCalled();
    });
  });
});
