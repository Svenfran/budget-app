import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule, ModalController } from '@ionic/angular';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { aCart, aCategory, aGroup, aLoggedInUser, aUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { OverviewService } from '../overview/service/overview.service';
import { Cart } from './model/cart';
import { CartlistPage } from './cartlist.page';
import { CartService } from './service/cart.service';
import { ExportDialogComponent } from './export-dialog/export-dialog.component';
import { FilterSheetComponent } from './filter-sheet/filter-sheet.component';
import { CartFilter } from './model/cart-filter';

// DIV-36: Lade-Effect der Einkaufsliste (gleiche Struktur wie Shopping/Overview). Template ausgeblendet – geprüft wird
// nur die Effect-Logik, nicht Virtual Scroll oder Darstellung.
describe('CartlistPage – Neuladen nach Logout/Login', () => {
  let fixture: ComponentFixture<CartlistPage>;
  let groupService: GroupService;
  let session: BehaviorSubject<User | null>;
  let load: jasmine.Spy;

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    load = jasmine.createSpy('getCartListByGroupId')
      .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.());
    const cartList = signal<Cart[]>([]);
    const cartService = {
      cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
      sum: computed(() => 0), count: computed(() => cartList().length),
      getCartListByGroupId: load,
    };
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        { provide: CartService, useValue: cartService },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: CategoryService, useValue: { categoryUpdate: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    TestBed.overrideTemplate(CartlistPage, '');
    groupService = TestBed.inject(GroupService);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    load.calls.reset();
  });

  it('Logout und Login mit derselben Gruppe ohne Change Detection dazwischen (abgehängte View): genau ein Neuladen', () => {
    session.next(null);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture.detectChanges();

    expect(load).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), false, jasmine.any(Function));
  });
});

// DIV-68: Der Menüpunkt „Download“ öffnet den Export-Dialog; die Wegwisch-Sperre hängt an genau diesem Modal.
describe('CartlistPage – Export-Dialog', () => {
  let page: CartlistPage;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let modal: jasmine.SpyObj<HTMLIonModalElement>;

  beforeEach(() => {
    modal = jasmine.createSpyObj<HTMLIonModalElement>('HTMLIonModalElement', ['present']);
    modal.present.and.resolveTo();
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['create']);
    modalCtrl.create.and.resolveTo(modal);
    const cartList = signal<Cart[]>([]);
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => 0), getCartListByGroupId: jasmine.createSpy('load'),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: CategoryService, useValue: { categoryUpdate: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(null) },
        { provide: AlertService, useValue: alertServiceSpy() },
        { provide: ModalController, useValue: modalCtrl },
      ],
    });
    TestBed.overrideTemplate(CartlistPage, '');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    const fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
  });

  it('erzeugt genau ein Export-Modal mit der aktiven Gruppe und präsentiert dieses Modal', async () => {
    await page.openExportDialog();

    expect(modalCtrl.create).toHaveBeenCalledTimes(1);
    const options = modalCtrl.create.calls.mostRecent().args[0];
    expect(options.component).toBe(ExportDialogComponent);
    expect(options.componentProps?.['group']).toEqual(jasmine.objectContaining({ id: 3 }));
    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto']);
    expect(modal.present).toHaveBeenCalledTimes(1);
  });

  it('die an create übergebene Sperre liest das exporting-Signal, das der Dialog erhält', async () => {
    await page.openExportDialog();
    const options = modalCtrl.create.calls.mostRecent().args[0];
    const exporting = options.componentProps?.['exporting'] as WritableSignal<boolean>;
    const canDismiss = options.canDismiss as () => Promise<boolean>;

    expect(await canDismiss()).toBeTrue();
    exporting.set(true);
    expect(await canDismiss()).toBeFalse();
    exporting.set(false);
    expect(await canDismiss()).toBeTrue();
  });
});

// DIV-71: Charakterisierung der bestehenden Filterlogik vor dem Umbau auf das Filter-Bottom-Sheet. filterCarts, onFilter
// und deleteFilter bleiben unverändert; Anzahl und Summe rechnet der Fake wie CartService aus cartList.
describe('CartlistPage – Filter (Bestand)', () => {
  let page: CartlistPage;
  let cartList: WritableSignal<Cart[]>;
  let initCartList: WritableSignal<Cart[]>;
  let count: () => number;
  let sum: () => number;

  const sven = aUser({ userName: 'Sven' });
  const anna = aUser({ userName: 'Anna' });
  const ben = aUser({ userName: 'Ben' });
  const lebensmittel = aCategory({ name: 'Lebensmittel' });
  const drogerie = aCategory({ name: 'Drogerie' });
  const freizeit = aCategory({ name: 'Freizeit' });

  const edeka = aCart({ title: 'Edeka', description: 'Wocheneinkauf', amount: 40, userDto: sven,
    categoryDto: lebensmittel, datePurchased: new Date(2025, 5, 1) });
  const rossmann = aCart({ title: 'Rossmann', description: 'Zahnpasta', amount: 10.5, userDto: anna,
    categoryDto: drogerie, datePurchased: new Date(2025, 5, 10) });
  const kino = aCart({ title: 'Kino', description: 'Film mit Edeka-Gutschein', amount: 25, userDto: ben,
    categoryDto: freizeit, datePurchased: new Date(2025, 5, 20) });
  const edekaSpaet = aCart({ title: 'EDEKA Markt', description: 'Getränke', amount: 12, userDto: anna,
    categoryDto: lebensmittel, datePurchased: new Date(2025, 5, 20, 18, 0) });
  const alle = [edeka, rossmann, kino, edekaSpaet];

  const titles = () => cartList().map(cart => cart.title);

  beforeEach(() => {
    cartList = signal<Cart[]>([]);
    initCartList = signal<Cart[]>([]);
    count = computed(() => cartList().length);
    sum = computed(() => cartList().reduce((s, c) => s + (+c.amount), 0));
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList, cartUpdated: signal(0), sum, count,
            getCartListByGroupId: jasmine.createSpy('load'),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: CategoryService, useValue: { categoryUpdate: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(null) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    TestBed.overrideTemplate(CartlistPage, '');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    const fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
    initCartList.set(alle);
    cartList.set(alle);
  });

  it('Titel: Teilstring ohne Beachtung der Groß-/Kleinschreibung', () => {
    page.filterCarts({ title: 'edeka' });

    expect(titles()).toEqual(['Edeka', 'EDEKA Markt']);
    expect(count()).toBe(2);
    expect(sum()).toBe(52);
  });

  it('Beschreibung: Teilstring ohne Beachtung der Groß-/Kleinschreibung', () => {
    page.filterCarts({ description: 'EDEKA' });

    expect(titles()).toEqual(['Kino']);
    expect(count()).toBe(1);
    expect(sum()).toBe(25);
  });

  it('mehrere Kategorien: Cart passt zu einer davon', () => {
    page.filterCarts({ category: ['Drogerie', 'Freizeit'] });

    expect(titles()).toEqual(['Rossmann', 'Kino']);
    expect(count()).toBe(2);
    expect(sum()).toBe(35.5);
  });

  it('mehrere Ersteller: Cart passt zu einem davon', () => {
    page.filterCarts({ userName: ['Sven', 'Ben'] });

    expect(titles()).toEqual(['Edeka', 'Kino']);
    expect(count()).toBe(2);
    expect(sum()).toBe(65);
  });

  it('Datum von: inklusiv ab lokaler Mitternacht', () => {
    page.filterCarts({ startDate: new Date(2025, 5, 10) });

    expect(titles()).toEqual(['Rossmann', 'Kino', 'EDEKA Markt']);
    expect(count()).toBe(3);
    expect(sum()).toBe(47.5);
  });

  // Regression (DIV-71, Fix): früher war „bis“ lokale Mitternacht, spätere Uhrzeiten am Enddatum fielen heraus
  it('Datum bis: einschließlich des ganzen Tages, auch spätere Uhrzeiten', () => {
    page.filterCarts({ endDate: new Date(2025, 5, 20) });

    expect(titles()).toEqual(['Edeka', 'Rossmann', 'Kino', 'EDEKA Markt']);
    expect(count()).toBe(4);
    expect(sum()).toBe(87.5);
  });

  it('Datum bis: schließt den Folgetag aus', () => {
    page.filterCarts({ endDate: new Date(2025, 5, 19) });

    expect(titles()).toEqual(['Edeka', 'Rossmann']);
  });

  it('Datum von = bis: genau die Carts dieses Tages', () => {
    page.filterCarts({ startDate: new Date(2025, 5, 20), endDate: new Date(2025, 5, 20) });

    expect(titles()).toEqual(['Kino', 'EDEKA Markt']);
  });

  // Format aus dem Backend (DATE-Spalte, JSON ohne Uhrzeit): zählt wie in der Anzeige (date-Pipe) zu genau diesem
  // Tag, unabhängig von der Zeitzone (Codex-Review F-001).
  it('Datum von/bis: Kaufdatum im Backend-Format „yyyy-MM-dd“ zählt zu diesem Tag', () => {
    const backendCart = aCart({ title: 'Backend', amount: 5, userDto: sven, categoryDto: lebensmittel,
      datePurchased: '2025-06-20' as unknown as Date });
    initCartList.set([...alle, backendCart]);

    page.filterCarts({ startDate: new Date(2025, 5, 20), endDate: new Date(2025, 5, 20) });

    expect(titles()).toEqual(['Kino', 'EDEKA Markt', 'Backend']);
  });

  it('kombiniert mehrere Kriterien mit UND (AC 4)', () => {
    page.filterCarts({
      title: 'edeka', category: ['Lebensmittel'], userName: ['Anna'],
      startDate: new Date(2025, 5, 15), endDate: new Date(2025, 5, 21),
    });

    expect(titles()).toEqual(['EDEKA Markt']);
    expect(count()).toBe(1);
    expect(sum()).toBe(12);
  });

  it('filtert immer von der vollständigen Liste aus, nicht von der zuvor gefilterten', () => {
    page.filterCarts({ userName: ['Sven'] });
    page.filterCarts({ userName: ['Anna'] });

    expect(titles()).toEqual(['Rossmann', 'EDEKA Markt']);
  });

  it('setzt filterMode (Filter-Chip) und leert das Schnellfilter-Label', () => {
    page.onFilter('category', 'Drogerie');
    page.filterCarts({ title: 'Kino' });

    expect(page.filterMode()).toBeTrue();
    expect(page.filterTerm()).toBe('');
  });

  it('alle Felder null → Filter aufgehoben, volle Liste', () => {
    page.filterCarts({ title: 'Kino' });

    page.filterCarts({ title: null, description: null, category: null, startDate: null, endDate: null, userName: null });

    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual({});
    expect(titles()).toEqual(['Edeka', 'Rossmann', 'Kino', 'EDEKA Markt']);
    expect(count()).toBe(4);
    expect(sum()).toBe(87.5);
  });

  it('deleteFilter (X am Chip) → volle Liste, Filter leer', () => {
    page.filterCarts({ userName: ['Sven'] });
    page.cartFilter = { userName: ['Sven'] };

    page.deleteFilter();

    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual({});
    expect(count()).toBe(4);
    expect(sum()).toBe(87.5);
  });

  it('Schnellfilter Kategorie: belegt cartFilter.category vor (für das erneute Öffnen des Filters, AC 3)', () => {
    page.onFilter('category', 'Lebensmittel');

    expect(page.cartFilter).toEqual({ category: ['Lebensmittel'] });
    expect(page.filterTerm()).toBe('Lebensmittel');
    expect(page.filterMode()).toBeTrue();
    expect(titles()).toEqual(['Edeka', 'EDEKA Markt']);
    expect(sum()).toBe(52);
  });

  it('Schnellfilter Ersteller: belegt cartFilter.userName vor; erneuter Tipp hebt ihn auf', () => {
    page.onFilter('user', 'Anna');

    expect(page.cartFilter).toEqual({ userName: ['Anna'] });
    expect(count()).toBe(2);

    page.onFilter('user', 'Anna');

    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual({});
    expect(count()).toBe(4);
  });
});

// DIV-71: Menüpunkt „Filter“ und Filter-Chip öffnen das Filter-Bottom-Sheet; übernommen wird nur bei Rolle „apply“
// im unveränderten Kontext (aktive Gruppe, Anmelde-Sitzung).
describe('CartlistPage – Filter-Sheet', () => {
  let page: CartlistPage;
  let groupService: GroupService;
  let session: BehaviorSubject<User | null>;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let modal: jasmine.SpyObj<HTMLIonModalElement>;
  let resolveDismiss: (result: { data?: CartFilter; role?: string }) => void;
  let cartList: WritableSignal<Cart[]>;
  let count: () => number;
  let sum: () => number;

  const lebensmittel = aCategory({ name: 'Lebensmittel' });
  const drogerie = aCategory({ name: 'Drogerie' });
  const alle = [
    aCart({ title: 'Edeka', amount: 40, categoryDto: lebensmittel, userDto: aUser({ userName: 'Sven' }) }),
    aCart({ title: 'Rossmann', amount: 10.5, categoryDto: drogerie, userDto: aUser({ userName: 'Anna' }) }),
    aCart({ title: 'Rewe', amount: 20, categoryDto: lebensmittel, userDto: aUser({ userName: 'Anna' }) }),
  ];
  const noCriteria: CartFilter = {
    title: null, description: null, category: null, startDate: null, endDate: null, userName: null,
  };

  /** Öffnet das Sheet und schließt es mit dem gegebenen Ergebnis; wartet, bis die Seite reagiert hat. */
  const dismissWith = async (result: { data?: CartFilter; role?: string }) => {
    resolveDismiss(result);
    await new Promise(resolve => setTimeout(resolve));
  };

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    modal = jasmine.createSpyObj<HTMLIonModalElement>('HTMLIonModalElement', ['present', 'onDidDismiss']);
    modal.present.and.resolveTo();
    modal.onDidDismiss.and.callFake(() => new Promise(resolve => resolveDismiss = resolve) as never);
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['create']);
    modalCtrl.create.and.resolveTo(modal);
    cartList = signal<Cart[]>([]);
    const initCartList = signal<Cart[]>([]);
    count = computed(() => cartList().length);
    sum = computed(() => cartList().reduce((s, c) => s + (+c.amount), 0));
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList, cartUpdated: signal(0), sum, count,
            getCartListByGroupId: jasmine.createSpy('load'),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: CategoryService, useValue: { categoryUpdate: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
        { provide: ModalController, useValue: modalCtrl },
      ],
    });
    TestBed.overrideTemplate(CartlistPage, '');
    groupService = TestBed.inject(GroupService);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    const fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
    initCartList.set(alle);
    cartList.set(alle);
  });

  it('öffnet genau ein Filter-Sheet mit einer Kopie des angewendeten Filters (AC 1, AC 3)', async () => {
    page.onFilter('category', 'Drogerie');

    await page.openFilterSheet();

    expect(modalCtrl.create).toHaveBeenCalledTimes(1);
    const options = modalCtrl.create.calls.mostRecent().args[0];
    expect(options.component).toBe(FilterSheetComponent);
    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'filter-sheet']);
    const passed = options.componentProps?.['cartFilter'] as CartFilter;
    expect(passed).toEqual(jasmine.objectContaining({ category: ['Drogerie'] }));
    expect(passed).not.toBe(page.cartFilter);
    expect(passed.category).not.toBe(page.cartFilter.category);
    expect(modal.present).toHaveBeenCalledTimes(1);
  });

  it('„Filter anwenden“: übernimmt den Filter, Liste, Chip, Anzahl und Summe passen (AC 5, AC 8)', async () => {
    await page.openFilterSheet();
    const applied: CartFilter = { ...noCriteria, category: ['Lebensmittel'], userName: ['Anna'] };

    await dismissWith({ data: applied, role: 'apply' });

    expect(page.cartFilter).toBe(applied);
    expect(page.filterMode()).toBeTrue();
    expect(cartList().map(cart => cart.title)).toEqual(['Rewe']);
    expect(count()).toBe(1);
    expect(sum()).toBe(20);
  });

  it('Abbrechen (X), Backdrop oder Hardware-Back: angewendeter Filter, Liste, Anzahl und Summe unverändert (AC 7)', async () => {
    page.filterCarts({ category: ['Lebensmittel'] });
    page.cartFilter = { category: ['Lebensmittel'] };

    for (const result of [{ role: 'cancel' }, { role: 'backdrop' }, { role: 'gesture' }, {}]) {
      await page.openFilterSheet();
      await dismissWith(result);

      expect(page.cartFilter).toEqual({ category: ['Lebensmittel'] });
      expect(page.filterMode()).toBeTrue();
      expect(count()).toBe(2);
      expect(sum()).toBe(60);
    }
  });

  it('Daten ohne Rolle apply werden nicht übernommen', async () => {
    await page.openFilterSheet();

    await dismissWith({ data: { ...noCriteria, title: 'Edeka' }, role: 'cancel' });

    expect(page.filterMode()).toBeFalse();
    expect(count()).toBe(3);
  });

  it('leere Felder anwenden hebt den Filter auf: volle Anzahl und Summe (AC 6)', async () => {
    page.filterCarts({ category: ['Drogerie'] });
    page.cartFilter = { category: ['Drogerie'] };
    await page.openFilterSheet();

    await dismissWith({ data: noCriteria, role: 'apply' });

    // wie bisher: filterCarts setzt zurück, danach wird der übernommene Filter (ohne Kriterium) gespeichert
    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual(noCriteria);
    expect(count()).toBe(3);
    expect(sum()).toBe(70.5);
  });

  it('Gruppenwechsel bei offenem Sheet: „Filter anwenden“ wird verworfen', async () => {
    await page.openFilterSheet();
    groupService.setActiveGroup(aGroup({ id: 4, name: 'Büro' }));

    await dismissWith({ data: { ...noCriteria, category: ['Drogerie'] }, role: 'apply' });

    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual({});
    expect(count()).toBe(3);
  });

  // Codex-Review 1, F-005: activeGroupId ist nach Logout/Login mit derselben Gruppe wieder gleich, session nicht
  it('Logout und Login mit derselben Gruppe bei offenem Sheet: „Filter anwenden“ wird verworfen', async () => {
    await page.openFilterSheet();
    session.next(null);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 3, name: 'WG' }));

    await dismissWith({ data: { ...noCriteria, category: ['Drogerie'] }, role: 'apply' });

    expect(page.filterMode()).toBeFalse();
    expect(page.cartFilter).toEqual({});
    expect(count()).toBe(3);
  });
});
