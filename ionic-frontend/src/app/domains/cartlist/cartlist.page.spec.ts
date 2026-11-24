import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Router, RouterModule, UrlTree } from '@angular/router';
import { AlertController, IonicModule, IonItemSliding, ModalController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { CategoryService } from 'src/app/service/category.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { ionicReady } from 'src/testing/ionic-ready';
import { aCart, aCategory, aGroup, aLoggedInUser, aUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { OverviewService } from '../overview/service/overview.service';
import { Cart } from './model/cart';
import { CartlistPage } from './cartlist.page';
import { CartService } from './service/cart.service';
import { ExportDialogComponent } from './export-dialog/export-dialog.component';
import { FilterSheetComponent } from './filter-sheet/filter-sheet.component';
import { SettlementSheetComponent } from './settlement-sheet/settlement-sheet.component';
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

// DIV-70: Der Menüpunkt „Ausgleichszahlung“ öffnet das Ausgleichszahlungs-Sheet; die Schließ-Sperre hängt an genau
// diesem Modal. Gespeichert wird im Sheet selbst (kein onDidDismiss auf der Seite).
describe('CartlistPage – Ausgleichszahlung', () => {
  let page: CartlistPage;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let modal: jasmine.SpyObj<HTMLIonModalElement>;
  let alertCtrl: jasmine.SpyObj<AlertController>;
  let alertEl: jasmine.SpyObj<HTMLIonAlertElement>;
  let answer: (role: string | undefined) => void;

  beforeEach(() => {
    alertEl = jasmine.createSpyObj<HTMLIonAlertElement>('HTMLIonAlertElement', ['present', 'onDidDismiss']);
    alertEl.present.and.resolveTo();
    alertEl.onDidDismiss.and.callFake(() => new Promise(resolve => answer = role => resolve({ role })));
    alertCtrl = jasmine.createSpyObj<AlertController>('AlertController', ['create']);
    alertCtrl.create.and.resolveTo(alertEl);
    modal = jasmine.createSpyObj<HTMLIonModalElement>('HTMLIonModalElement', ['present']);
    modal.present.and.resolveTo();
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['create', 'dismiss']);
    modalCtrl.create.and.resolveTo(modal);
    modalCtrl.dismiss.and.resolveTo(true);
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
        { provide: AlertController, useValue: alertCtrl },
      ],
    });
    TestBed.overrideTemplate(CartlistPage, '');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    const fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
  });

  it('erzeugt genau ein Ausgleichszahlungs-Sheet und präsentiert es (AC 1)', async () => {
    await page.openSettlementSheet();

    expect(modalCtrl.create).toHaveBeenCalledTimes(1);
    const options = modalCtrl.create.calls.mostRecent().args[0];
    expect(options.component).toBe(SettlementSheetComponent);
    expect(options.cssClass).toEqual(['bottom-sheet', 'bottom-sheet-auto', 'settlement-sheet']);
    expect(options.componentProps?.['modalId']).toBe(options.id);
    expect(modal.present).toHaveBeenCalledTimes(1);
  });

  it('die an create übergebene Sperre liest das saving-Signal, das das Sheet erhält', async () => {
    await page.openSettlementSheet();
    const options = modalCtrl.create.calls.mostRecent().args[0];
    const saving = options.componentProps?.['saving'] as WritableSignal<boolean>;
    const canDismiss = options.canDismiss as () => Promise<boolean>;

    expect(saving()).toBeFalse();
    expect(await canDismiss()).toBeTrue();
    saving.set(true);
    expect(await canDismiss()).toBeFalse();
  });

  it('jedes Öffnen bekommt ein eigenes saving-Signal', async () => {
    await page.openSettlementSheet();
    await page.openSettlementSheet();
    const [first, second] = modalCtrl.create.calls.allArgs().map(args => args[0].componentProps?.['saving']);

    expect(first).not.toBe(second);
  });

  // Phase 3 (E1): Rückfrage beim Schließen mit geänderten Eingaben
  describe('Rückfrage „Eingaben verwerfen?“', () => {
    const key = (suffix: string) => `domains.spendings.overlay.settlementpayment.discard.${suffix}`;
    /** wartet, bis der Alert präsentiert ist (create/present laufen asynchron) */
    const alertShown = async () => {
      for (let i = 0; i < 10 && !alertEl.present.calls.count(); i++) await Promise.resolve();
      expect(alertEl.present).toHaveBeenCalled();
    };

    it('zeigt einen Alert mit Titel, Text und den Aktionen „Weiter bearbeiten“ / „Verwerfen“', async () => {
      const result = page.confirmDiscard();
      await alertShown();

      expect(alertCtrl.create).toHaveBeenCalledOnceWith(jasmine.objectContaining({
        header: key('header'),
        message: key('message'),
        buttons: [
          { text: key('btn_continue'), role: 'cancel' },
          { text: key('btn_discard'), role: 'destructive' },
        ],
      }));
      answer('destructive');
      expect(await result).toBeTrue();
    });

    it('„Weiter bearbeiten“, Backdrop oder Hardware-Back am Alert: nicht verwerfen', async () => {
      for (const role of ['cancel', 'backdrop', undefined]) {
        alertEl.present.calls.reset();
        const result = page.confirmDiscard();
        await alertShown();
        answer(role);
        expect(await result).toBeFalse();
      }
    });

    it('gleichzeitige Auslöser (z. B. Doppel-Tipp) teilen sich einen Alert; danach fragt es wieder neu', async () => {
      const first = page.confirmDiscard();
      const second = page.confirmDiscard();
      await alertShown();

      expect(alertCtrl.create).toHaveBeenCalledTimes(1);
      answer('cancel');
      expect(await first).toBeFalse();
      expect(await second).toBeFalse();

      alertEl.present.calls.reset();
      const third = page.confirmDiscard();
      await alertShown();
      expect(alertCtrl.create).toHaveBeenCalledTimes(2);
      answer('destructive');
      expect(await third).toBeTrue();
    });

    it('das geöffnete Sheet fragt bei geänderten Eingaben über die Seite nach', async () => {
      await page.openSettlementSheet();
      const options = modalCtrl.create.calls.mostRecent().args[0];
      const dirty = options.componentProps?.['dirty'] as WritableSignal<boolean>;
      const canDismiss = options.canDismiss as (data?: unknown, role?: string) => Promise<boolean>;

      expect(await canDismiss(undefined, 'backdrop')).toBeTrue();
      expect(alertCtrl.create).not.toHaveBeenCalled();

      dirty.set(true);
      expect(await canDismiss(undefined, 'backdrop')).toBeFalse();
      await alertShown();
      answer('cancel');
      await new Promise(resolve => setTimeout(resolve));
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    // F-001: nach „Verwerfen“ schließt das Sheet sich über seine eigene Modal-ID
    it('„Verwerfen“ schließt genau dieses Sheet über den ModalController', async () => {
      await page.openSettlementSheet();
      const options = modalCtrl.create.calls.mostRecent().args[0];
      (options.componentProps?.['dirty'] as WritableSignal<boolean>).set(true);
      const canDismiss = options.canDismiss as (data?: unknown, role?: string) => Promise<boolean>;

      expect(await canDismiss(undefined, 'cancel')).toBeFalse();
      await alertShown();
      answer('destructive');
      await new Promise(resolve => setTimeout(resolve));

      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, 'cancel', options.id);
    });
  });
});

// DIV-74: Einkäufe einer Ausgleichszahlung werden im Settlement-Sheet bearbeitet (nicht im normalen Formular) und immer
// paarweise über den Settlement-Endpoint gelöscht. Template ausgeblendet – geprüft werden die Methoden hinter den Wischaktionen.
describe('CartlistPage – Wischaktionen bei Ausgleichszahlungen (DIV-74)', () => {
  let page: CartlistPage;
  let groupService: GroupService;
  let session: BehaviorSubject<User | null>;
  let alertCtrl: jasmine.SpyObj<AlertController>;
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let alerts: jasmine.SpyObj<AlertService>;
  let initCartList: WritableSignal<Cart[]>;
  let load: jasmine.Spy;
  let cartService: { deleteCart: jasmine.Spy; deleteSettlementPayment: jasmine.Spy };
  const slidingItem = () => jasmine.createSpyObj<IonItemSliding>('IonItemSliding', ['close']);
  const anna = aUser({ userName: 'Anna' });
  const ben = aUser({ userName: 'Ben' });
  const settlementCart = aCart({ id: 11, title: 'Ausgleichszahlung an Ben', amount: 30, userDto: anna, settlementId: 70,
    datePurchased: '2025-06-10' });
  const partnerCart = aCart({ id: 13, title: 'Ausgleichszahlung von Anna', amount: -30, userDto: ben, settlementId: 70,
    datePurchased: '2025-06-10' });
  const normalCart = aCart({ id: 12, title: 'Pizza' });

  /** Optionen des zuletzt erzeugten Alerts und der Handler des „Ja“-Buttons. */
  const lastAlert = () => {
    const options = alertCtrl.create.calls.mostRecent().args[0]!;
    const ok = (options.buttons as { role?: string; handler?: () => void }[]).find(b => b.role !== 'cancel')!;
    return { options, confirm: () => ok.handler!() };
  };

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    const alertEl = jasmine.createSpyObj<HTMLIonAlertElement>('HTMLIonAlertElement', ['present']);
    alertEl.present.and.resolveTo();
    alertCtrl = jasmine.createSpyObj<AlertController>('AlertController', ['create']);
    alertCtrl.create.and.resolveTo(alertEl);
    cartService = {
      deleteCart: jasmine.createSpy('deleteCart'),
      deleteSettlementPayment: jasmine.createSpy('deleteSettlementPayment'),
    };
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['create', 'dismiss']);
    const modal = jasmine.createSpyObj<HTMLIonModalElement>('HTMLIonModalElement', ['present']);
    modal.present.and.resolveTo();
    modalCtrl.create.and.resolveTo(modal);
    alerts = alertServiceSpy();
    initCartList = signal<Cart[]>([]);
    load = jasmine.createSpy('load');
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            ...cartService, cartList: signal<Cart[]>([]), initCartList, cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => 0), getCartListByGroupId: load,
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: CategoryService, useValue: { categoryUpdate: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alerts },
        { provide: AlertController, useValue: alertCtrl },
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
  });

  it('„Bearbeiten“ eines normalen Einkaufs öffnet das Formular', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    page.editCart(normalCart, slidingItem());

    expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist', 'new-edit', '12']);
    expect(modalCtrl.create).not.toHaveBeenCalled();
  });

  it('„Bearbeiten“ einer Ausgleichszahlung öffnet das Settlement-Sheet im Edit-Modus, vorbelegt aus dem Cart-Paar', async () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    initCartList.set([normalCart, partnerCart, settlementCart]);

    page.editCart(settlementCart, slidingItem());
    await Promise.resolve();

    expect(navigate).not.toHaveBeenCalled();
    expect(modalCtrl.create).toHaveBeenCalledTimes(1);
    const options = modalCtrl.create.calls.mostRecent().args[0];
    expect(options.component).toBe(SettlementSheetComponent);
    expect(options.componentProps?.['settlement']).toEqual({
      id: 70, role: 'payer', payer: { id: anna.id, userName: 'Anna' }, receiver: { id: ben.id, userName: 'Ben' },
      amount: 30, datePurchased: '2025-06-10',
    });
  });

  it('„Bearbeiten“ ohne Partner-Cart (veraltete Liste): Hinweis und stilles Neuladen, kein Sheet', () => {
    initCartList.set([settlementCart]);

    page.editCart(settlementCart, slidingItem());

    expect(modalCtrl.create).not.toHaveBeenCalled();
    expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
      'alerts.cart.settlement_not_found.header', 'alerts.cart.settlement_not_found.message');
    const [gruppe, refresh, , options] = load.calls.mostRecent().args;
    expect(gruppe.id).toBe(3);
    expect(refresh).toBeTrue();
    expect(options.silent).toBeTrue();
    expect(options.isCurrent()).toBeTrue();
    groupService.setActiveGroup(aGroup({ id: 4, name: 'Andere' }));
    expect(options.isCurrent()).toBeFalse();
  });

  it('Löschen eines Einkaufs einer Ausgleichszahlung fragt eigens nach und löscht das Paar über den Settlement-Endpoint', async () => {
    const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
    const item = slidingItem();

    page.onDeleteCart(settlementCart, item);
    await Promise.resolve();
    const { options, confirm } = lastAlert();
    confirm();

    expect(item.close).toHaveBeenCalled();
    expect(options.header).toBe('alerts.cart.delete_settlement.header');
    expect(translate).toHaveBeenCalledWith('alerts.cart.delete_settlement.message', { cartTitle: 'Ausgleichszahlung an Ben' });
    expect(cartService.deleteSettlementPayment).toHaveBeenCalledOnceWith(70, jasmine.objectContaining({ id: 3 }),
      jasmine.any(Function));
    expect(cartService.deleteCart).not.toHaveBeenCalled();
  });

  it('isCurrent des Löschens wird nach Gruppenwechsel oder neuer Anmelde-Sitzung false', async () => {
    page.onDeleteCart(settlementCart, slidingItem());
    await Promise.resolve();
    lastAlert().confirm();
    const isCurrent = cartService.deleteSettlementPayment.calls.mostRecent().args[2] as () => boolean;

    expect(isCurrent()).toBeTrue();
    groupService.setActiveGroup(aGroup({ id: 4, name: 'Andere' }));
    expect(isCurrent()).toBeFalse();

    groupService.setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    expect(isCurrent()).toBeTrue();
    session.next(null);
    session.next(aLoggedInUser({ id: 7 }));
    expect(isCurrent()).toBeFalse();
  });

  it('Löschen eines normalen Einkaufs bleibt beim bisherigen Endpoint', async () => {
    page.onDeleteCart(normalCart, slidingItem());
    await Promise.resolve();
    const { options } = lastAlert();

    expect(options.header).toBe('alerts.cart.delete.header');
    expect(cartService.deleteSettlementPayment).not.toHaveBeenCalled();
  });
});

// DIV-71: Charakterisierung der bestehenden Filterlogik vor dem Umbau auf das Filter-Bottom-Sheet. filterCarts, onFilter
// und deleteFilter bleiben unverändert; Anzahl und Summe rechnet der Fake wie CartService aus cartList.
describe('CartlistPage – Filter (Bestand)', () => {
  let page: CartlistPage;
  let cartList: WritableSignal<Cart[]>;
  let initCartList: WritableSignal<Cart[]>;
  let cartUpdated: WritableSignal<number>;
  let filterFixture: ComponentFixture<CartlistPage>;
  let count: () => number;
  let sum: () => number;

  const sven = aUser({ userName: 'Sven' });
  const anna = aUser({ userName: 'Anna' });
  const ben = aUser({ userName: 'Ben' });
  const lebensmittel = aCategory({ name: 'Lebensmittel' });
  const drogerie = aCategory({ name: 'Drogerie' });
  const freizeit = aCategory({ name: 'Freizeit' });

  const edeka = aCart({ title: 'Edeka', description: 'Wocheneinkauf', amount: 40, userDto: sven,
    categoryDto: lebensmittel, datePurchased: '2025-06-01' });
  const rossmann = aCart({ title: 'Rossmann', description: 'Zahnpasta', amount: 10.5, userDto: anna,
    categoryDto: drogerie, datePurchased: '2025-06-10' });
  const kino = aCart({ title: 'Kino', description: 'Film mit Edeka-Gutschein', amount: 25, userDto: ben,
    categoryDto: freizeit, datePurchased: '2025-06-20' });
  const edekaSpaet = aCart({ title: 'EDEKA Markt', description: 'Getränke', amount: 12, userDto: anna,
    categoryDto: lebensmittel, datePurchased: '2025-06-20' });
  const alle = [edeka, rossmann, kino, edekaSpaet];

  const titles = () => cartList().map(cart => cart.title);

  beforeEach(() => {
    cartUpdated = signal(0);
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
            cartList, initCartList, cartUpdated, sum, count,
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
    filterFixture = TestBed.createComponent(CartlistPage);
    filterFixture.detectChanges();
    page = filterFixture.componentInstance;
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

  /** Simuliert eine erfolgreiche Änderung wie die Services: Eintrag in beiden Listen ersetzen, dann triggerUpdate(). */
  const erfolgreichGeaendert = (geaendert: Cart) => {
    initCartList.update(carts => carts.map(c => c.id === geaendert.id ? geaendert : c));
    cartList.update(carts => carts.map(c => c.id === geaendert.id ? geaendert : c));
    cartUpdated.update(n => n + 1);
    filterFixture.detectChanges();
  };

  // Regression DIV-74, Codex-Implementierungsreview F-001
  it('aktiver Datumsfilter: ein bearbeiteter Einkauf, der nicht mehr passt, verschwindet nach dem Speichern aus der Liste', () => {
    page.filterCarts({ startDate: new Date(2025, 5, 15) });
    expect(titles()).toEqual(['Kino', 'EDEKA Markt']);

    erfolgreichGeaendert({ ...kino, datePurchased: '2025-06-01' });

    expect(titles()).toEqual(['EDEKA Markt']);
    expect(count()).toBe(1);
    expect(sum()).toBe(12);
    expect(page.filterMode()).toBeTrue();
  });

  it('aktiver Schnellfilter Kategorie: Filter und Chip bleiben, die Liste folgt der Änderung (DIV-74)', () => {
    page.onFilter('category', 'Lebensmittel');
    expect(titles()).toEqual(['Edeka', 'EDEKA Markt']);

    erfolgreichGeaendert({ ...edeka, categoryDto: drogerie });

    expect(titles()).toEqual(['EDEKA Markt']);
    expect(page.filterTerm()).toBe('Lebensmittel');
    expect(page.cartFilter).toEqual({ category: ['Lebensmittel'] });
  });

  it('ohne aktiven Filter lässt eine erfolgreiche Änderung die Liste, wie der Service sie gesetzt hat (DIV-74)', () => {
    erfolgreichGeaendert({ ...kino, datePurchased: '2025-06-01' });

    expect(titles()).toEqual(['Edeka', 'Rossmann', 'Kino', 'EDEKA Markt']);
    expect(page.filterMode()).toBeFalse();
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

  // Format aus dem Backend (seit DIV-81 „YYYY-MM-DD“ ohne Uhrzeit): zählt wie in der Anzeige (date-Pipe) zu genau
  // diesem Tag, unabhängig von der Zeitzone (Codex-Review F-001, DIV-81 AC 6).
  it('Datum von/bis: Kaufdatum im Backend-Format „yyyy-MM-dd“ zählt zu diesem Tag und nicht zum Folgetag', () => {
    const backendCart = aCart({ title: 'Backend', amount: 5, userDto: sven, categoryDto: lebensmittel,
      datePurchased: '2025-06-20' });
    initCartList.set([...alle, backendCart]);

    page.filterCarts({ startDate: new Date(2025, 5, 20), endDate: new Date(2025, 5, 20) });

    expect(titles()).toEqual(['Kino', 'EDEKA Markt', 'Backend']);

    page.filterCarts({ startDate: new Date(2025, 5, 21) });

    expect(titles()).not.toContain('Backend');
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

  it('Schnellfilter Kategorie per Tipp: normale Kategorie mit ihrem Namen im Chip', () => {
    page.onFilterCategory(aCart({ categoryDto: lebensmittel }));

    expect(page.filterTerm()).toBe('Lebensmittel');
    expect(page.cartFilter).toEqual({ category: ['Lebensmittel'] });
  });

  it('Schnellfilter Settlement-Kategorie (DIV-74): Chip in der App-Sprache, gefiltert wird über den gespeicherten Namen', () => {
    const ausgleich = aCategory({ name: 'Ausgleichszahlung', settlement: true });
    const zahlung = aCart({ title: 'Ausgleichszahlung an Ben', amount: 20, categoryDto: ausgleich, settlementId: 70 });
    initCartList.set([...initCartList(), zahlung]);
    cartList.set(initCartList());
    spyOn(TestBed.inject(TranslateService), 'instant').and.callFake(
      ((key: string) => key === 'domains.spendings.settlement' ? 'Repayment' : key) as never);

    page.onFilterCategory(zahlung);

    expect(page.filterTerm()).toBe('Repayment');
    expect(page.cartFilter).toEqual({ category: ['Ausgleichszahlung'] });
    expect(titles()).toEqual(['Ausgleichszahlung an Ben']);
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

// DIV-73: Sliding-Aktion „Duplizieren“. Navigation ohne Template; Sichtbarkeit und Reihenfolge der Aktionen im echten
// Template. DIV-74: Ausgleichszahlungen werden an der `settlementId` erkannt, nicht am Kategorienamen.
describe('CartlistPage – Sliding-Aktionen (DIV-73)', () => {
  const anna = aUser({ userName: 'Anna' });
  const ben = aUser({ userName: 'Ben' });
  const eigener = aCart({ id: 11, title: 'Eigener Einkauf', userDto: anna, categoryDto: aCategory({ name: 'Lebensmittel' }) });
  const ausgleich = aCart({ id: 12, title: 'Ausgleich an Ben', userDto: anna, settlementId: 70,
    categoryDto: aCategory({ name: 'Rückzahlungen' }) });
  const namensgleich = aCart({ id: 14, title: 'Unverknüpft', userDto: anna,
    categoryDto: aCategory({ name: 'Ausgleichszahlung' }) });
  const fremder = aCart({ id: 13, title: 'Bens Einkauf', userDto: ben, categoryDto: aCategory({ name: 'Lebensmittel' }) });

  let cartList: WritableSignal<Cart[]>;
  let fixture: ComponentFixture<CartlistPage>;
  let page: CartlistPage;

  function setup(withTemplate: boolean) {
    cartList = signal<Cart[]>([]);
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot(), ScrollingModule, TranslateModule, RouterModule, ScientificCurrencyPipe],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => cartList().length),
            getCartListByGroupId: jasmine.createSpy('load')
              .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.()),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: 'Anna' })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    if (!withTemplate) TestBed.overrideTemplate(CartlistPage, '');
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', { domains: { spendings: {
      settlement: 'Ausgleichszahlung', duplicate: 'Duplizieren', edit: 'Bearbeiten', delete: 'Löschen' } } });
    translate.use('de');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
    page = fixture.componentInstance;
  }

  describe('Navigation', () => {
    let slidingItem: jasmine.SpyObj<IonItemSliding>;
    let navigate: jasmine.Spy;

    beforeEach(() => {
      setup(false);
      slidingItem = jasmine.createSpyObj<IonItemSliding>('IonItemSliding', ['close']);
      navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    });

    it('Duplizieren schließt das Sliding-Item und öffnet die Neuanlage mit duplicateOf (AC 2)', () => {
      page.duplicateCart(eigener, slidingItem);

      expect(slidingItem.close).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist', 'new-edit'],
        { queryParams: { duplicateOf: 11 } });
    });

    it('Bearbeiten navigiert wie bisher zur Bearbeitung (AC 13)', () => {
      page.editCart(eigener, slidingItem);

      expect(slidingItem.close).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledOnceWith(['domains', 'tabs', 'cartlist', 'new-edit', '11']);
    });
  });

  describe('Sichtbarkeit und Reihenfolge', () => {
    /** aria-labels der Optionen je Einkaufstitel, in Template-Reihenfolge. */
    function optionsByTitle(): Record<string, string[]> {
      const result: Record<string, string[]> = {};
      fixture.nativeElement.querySelectorAll('ion-item-sliding').forEach((sliding: HTMLElement) => {
        const title = sliding.querySelector('h2.titlestyle')!.textContent!.trim();
        result[title] = Array.from(sliding.querySelectorAll('ion-item-option'))
          .map(option => (option as HTMLElement).getAttribute('aria-label')!);
      });
      return result;
    }

    beforeEach(async () => {
      setup(true);
      cartList.set([eigener, ausgleich, namensgleich, fremder]);
      fixture.detectChanges();
      // Virtual Scroll rendert die Zeilen erst nach dem nächsten Animation Frame
      await new Promise(resolve => requestAnimationFrame(resolve));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    });

    it('eigene reguläre Ausgabe: Duplizieren → Bearbeiten → Löschen (AC 1, Z1)', () => {
      expect(optionsByTitle()['Eigener Einkauf']).toEqual(['Duplizieren', 'Bearbeiten', 'Löschen']);
    });

    it('eigene Ausgleichszahlung: kein Duplizieren, Bearbeiten und Löschen wie bisher (FA5)', () => {
      expect(optionsByTitle()['Ausgleich an Ben']).toEqual(['Bearbeiten', 'Löschen']);
    });

    it('Ausgleichszahlungs-Icon an der settlementId, auch in umbenannter Kategorie; unverknüpfter Cart zeigt Kürzel (DIV-74)', () => {
      const sliding = (title: string) => Array.from(fixture.nativeElement.querySelectorAll('ion-item-sliding') as NodeListOf<HTMLElement>)
        .find(s => s.querySelector('h2.titlestyle')!.textContent!.trim() === title)!;

      expect(sliding('Ausgleich an Ben').querySelector('.category-circle ion-icon[name="swap-horizontal-outline"]')).not.toBeNull();
      expect(sliding('Unverknüpft').querySelector('.category-circle ion-icon')).toBeNull();
      expect(sliding('Unverknüpft').querySelector('.category-circle p')!.textContent!.trim()).toBe('AU');
      expect(optionsByTitle()['Unverknüpft']).toEqual(['Duplizieren', 'Bearbeiten', 'Löschen']);
    });

    it('fremde Ausgabe: keine Aktionen (Z2)', () => {
      expect(optionsByTitle()['Bens Einkauf']).toEqual([]);
    });
  });
});

// DIV-79 Phase 1: Liste und Refresher hängen an einem stabilen Scroll-Container statt an einer festen Viewport-Höhe.
// Geprüft wird die Anbindung (ein Container, Neumessung, isAtTop) – die Geometrie selbst nur über die Größenänderung.
describe('CartlistPage – Scroll-Host (DIV-79)', () => {
  let cartList: WritableSignal<Cart[]>;
  let fixture: ComponentFixture<CartlistPage>;
  let page: CartlistPage;
  let finishLoading: () => void;

  const carts = (count: number) => Array.from({ length: count }, (_, i) =>
    aCart({ title: `Einkauf ${i}`, userDto: aUser({ userName: 'Ben' }), categoryDto: aCategory({ name: 'Lebensmittel' }) }));
  const root = () => fixture.nativeElement as HTMLElement;
  const hosts = () => root().querySelectorAll('.ion-content-scroll-host');
  const host = () => hosts()[0] as HTMLElement;
  const viewportElement = () => root().querySelector('cdk-virtual-scroll-viewport');
  /** ResizeObserver meldet sich nach dem nächsten Layout; zwei Frames reichen sicher. */
  const nextFrames = async () => {
    await new Promise(resolve => requestAnimationFrame(resolve));
    await new Promise(resolve => requestAnimationFrame(resolve));
  };

  beforeEach(async () => {
    cartList = signal<Cart[]>([]);
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot(), ScrollingModule, TranslateModule, RouterModule, ScientificCurrencyPipe],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => cartList().length),
            // Laden bleibt offen, bis der Test es abschließt (Wechsel Laden → Liste)
            getCartListByGroupId: jasmine.createSpy('load')
              .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => {
                finishLoading = () => onComplete?.();
              }),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: 'Anna' })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    fixture = TestBed.createComponent(CartlistPage);
    page = fixture.componentInstance;
    // wie in der App füllt die Seite den Viewport; ion-page setzt dort der Router-Outlet (Seite als Flex-Spalte,
    // ion-content erhält die Resthöhe unter dem Header)
    root().classList.add('ion-page');
    root().style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 600px;';
    fixture.detectChanges();
    await ionicReady(root());
  });

  /** Lädt die Liste fertig und wartet, bis der Viewport sie gemessen und gerendert hat. */
  async function showList(count: number) {
    cartList.set(carts(count));
    if (page.isLoading) finishLoading();
    fixture.detectChanges();
    await nextFrames();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('genau ein Scroll-Container, derselbe beim Wechsel Laden → Liste → leer → Liste (Refresher bleibt angebunden)', async () => {
    expect(page.isLoading).toBeTrue();
    expect(hosts().length).toBe(1);
    const loadingHost = host();
    expect(viewportElement()).toBeNull();

    await showList(3);
    expect(hosts().length).toBe(1);
    expect(host()).toBe(loadingHost);
    // füllt genau den Platz unter dem Header bis zum Seitenende (keine feste Höhe mehr)
    const headerBottom = root().querySelector('ion-header')!.getBoundingClientRect().bottom;
    expect(host().getBoundingClientRect().top).toBeCloseTo(headerBottom, 0);
    expect(host().getBoundingClientRect().bottom).toBeCloseTo(root().getBoundingClientRect().bottom, 0);
    const firstViewport = viewportElement();
    expect(firstViewport).not.toBeNull();
    expect(host().contains(firstViewport)).toBeTrue();

    cartList.set([]);
    fixture.detectChanges();
    expect(viewportElement()).toBeNull();
    expect(host()).toBe(loadingHost);

    await showList(3);
    expect(host()).toBe(loadingHost);
    expect(viewportElement()).not.toBeNull();
    expect(viewportElement()).not.toBe(firstViewport);
  });

  it('Höhenänderung ohne Fenster-Resize misst den jeweils aktuellen Viewport neu', async () => {
    await showList(3);
    const first = page.viewport!;
    const firstCheck = spyOn(first, 'checkViewportSize').and.callThrough();

    const before = host().clientHeight;
    root().style.height = '400px';
    await nextFrames();
    expect(host().clientHeight).not.toBe(before);
    expect(firstCheck).toHaveBeenCalled();

    // Viewport wird neu erzeugt (leer → Liste): gemessen wird der neue, nicht mehr der alte
    cartList.set([]);
    fixture.detectChanges();
    await showList(3);
    const second = page.viewport!;
    expect(second).not.toBe(first);
    const secondCheck = spyOn(second, 'checkViewportSize').and.callThrough();
    firstCheck.calls.reset();

    root().style.height = '500px';
    await nextFrames();
    expect(secondCheck).toHaveBeenCalled();
    expect(firstCheck).not.toHaveBeenCalled();
  });

  it('isAtTop folgt dem Scroll-Offset des Containers und steuert disabled des Refreshers', async () => {
    await showList(60);
    const refresher = root().querySelector('ion-refresher') as HTMLIonRefresherElement;
    expect(page.isAtTop).toBeTrue();
    expect(refresher.disabled).toBeFalse();

    host().scrollTop = 500;
    expect(host().scrollTop).toBeGreaterThan(100); // Voraussetzung: Liste ist höher als der Container
    host().dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    expect(page.isAtTop).toBeFalse();
    expect(refresher.disabled).toBeTrue();

    host().scrollTop = 0;
    host().dispatchEvent(new Event('scroll'));
    fixture.detectChanges();
    expect(page.isAtTop).toBeTrue();
    expect(refresher.disabled).toBeFalse();
  });
});

// DIV-79 Phase 2: zweizeiliger Header – Titel, darunter die aktive Gruppe als Button zur Gruppenübersicht; rechts nur
// noch „+“ und das Overflow-Menü. Gekürzt wird nur per CSS, im DOM steht der volle Name.
describe('CartlistPage – Header (DIV-79)', () => {
  const longName = 'Wohngemeinschaft Musterstraße 12';
  let fixture: ComponentFixture<CartlistPage>;
  let groupService: GroupService;
  const root = () => fixture.nativeElement as HTMLElement;
  const groupButton = () => root().querySelector('ion-title button.page-title') as HTMLButtonElement;

  beforeEach(() => {
    const cartList = signal<Cart[]>([]);
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot(), ScrollingModule, TranslateModule, RouterModule, ScientificCurrencyPipe],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => cartList().length),
            getCartListByGroupId: jasmine.createSpy('load')
              .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.()),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: 'Anna' })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', {
      global: { change_group: 'Gruppe wechseln, aktuell: {{name}}' },
      domains: { spendings: { title: 'Ausgaben' } },
    });
    translate.use('de');
    groupService = TestBed.inject(GroupService);
    groupService.setActiveGroup(aGroup({ id: 3, name: longName }));
    fixture = TestBed.createComponent(CartlistPage);
    fixture.detectChanges();
  });

  it('Titel und darunter der volle Gruppenname (kein slice), Button mit beschreibendem aria-label (AC 1, 2)', () => {
    expect(root().querySelector('ion-title .page-title__text')!.textContent!.trim()).toBe('Ausgaben');
    expect(groupButton().querySelector('.page-title__group-name')!.textContent!.trim()).toBe(longName);
    // Titel und Gruppe bilden einen Button; das aria-label nennt Seite und Aktion
    expect(groupButton().querySelector('.page-title__text')!.textContent!.trim()).toBe('Ausgaben');
    expect(groupButton().getAttribute('aria-label')).toBe(`Ausgaben, Gruppe wechseln, aktuell: ${longName}`);
    expect(groupButton().querySelector('ion-icon')!.getAttribute('name')).toBe('chevron-down-outline');
  });

  it('Tipp auf die Gruppe navigiert zur Gruppenübersicht (AC 2)', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    groupButton().click();

    expect(navigate).toHaveBeenCalledTimes(1);
    expect(router.serializeUrl(navigate.calls.mostRecent().args[0] as UrlTree)).toBe('/groupoverview');
  });

  it('Umbenennung der aktiven Gruppe aktualisiert den Header', () => {
    groupService.setActiveGroup(aGroup({ id: 3, name: 'Neue WG' }));
    fixture.detectChanges();

    expect(groupButton().querySelector('.page-title__group-name')!.textContent!.trim()).toBe('Neue WG');
  });

  // Codex-Review F-002: der Wrapper von ion-title schneidet per overflow: hidden ab – gemessen wird die tatsächlich
  // treffbare Fläche, nicht nur die Box (gemeinsames Styling in global.scss, gilt für alle drei Tabs)
  it('Gruppen-Button: volle 44px Tippfläche, auch am oberen und unteren Rand treffbar (AC 2)', async () => {
    root().classList.add('ion-page');
    root().style.cssText = 'position: fixed; top: 0; left: 0; width: 360px; height: 640px;';
    fixture.detectChanges();
    await ionicReady(root());
    await new Promise(resolve => requestAnimationFrame(resolve));

    const box = groupButton().getBoundingClientRect();
    const x = box.left + Math.min(box.width / 2, 20);
    expect(box.height).toBeGreaterThanOrEqual(44);
    expect(groupButton().contains(document.elementFromPoint(x, box.top + 2))).withContext('oberer Rand').toBeTrue();
    expect(groupButton().contains(document.elementFromPoint(x, box.bottom - 2))).withContext('unterer Rand').toBeTrue();
  });

  it('rechts nur noch „+“ und Overflow-Menü, kein Gruppenname (AC 3)', () => {
    const actions = root().querySelector('ion-buttons[slot="primary"]')!;
    expect(actions.textContent).not.toContain(longName);
    const buttons = Array.from(actions.querySelectorAll(':scope > ion-button'));
    expect(buttons.length).toBe(2);
    expect(buttons[0].querySelector('ion-icon')!.getAttribute('name')).toBe('add');
    expect(buttons[1].id).toBe('cartlist-popover-button');
    expect(actions.querySelector('ion-popover')!.getAttribute('trigger')).toBe('cartlist-popover-button');
  });
});

// DIV-79 Phase 3: Zusammenfassung (Bezeichnung, Anzahl, Summe) und Filterzeile (Filter-Schaltfläche, Chip mit „×“) mit
// echtem Template. CartService-Fake wie „Filter (Bestand)“: Anzahl und Summe werden aus der gefilterten Liste berechnet.
// Die Filterlogik selbst bleibt unverändert und ist dort abgedeckt; hier zählt, was angezeigt wird.
describe('CartlistPage – Zusammenfassung und Filterzeile (DIV-79)', () => {
  let page: CartlistPage;
  let fixture: ComponentFixture<CartlistPage>;
  let cartList: WritableSignal<Cart[]>;
  let initCartList: WritableSignal<Cart[]>;

  const sven = aUser({ userName: 'Sven' });
  const anna = aUser({ userName: 'Anna' });
  const lebensmittel = aCategory({ name: 'Lebensmittel' });
  const drogerie = aCategory({ name: 'Drogerie' });
  const edeka = aCart({ title: 'Edeka', amount: 40, userDto: sven, categoryDto: lebensmittel });
  const rossmann = aCart({ title: 'Rossmann', amount: 10.5, userDto: anna, categoryDto: drogerie });
  const markt = aCart({ title: 'Markt', amount: 12, userDto: anna, categoryDto: lebensmittel });
  const alle = [edeka, rossmann, markt];
  const noFilter = { title: null, description: null, category: null, startDate: null, endDate: null, userName: null };

  const root = () => fixture.nativeElement as HTMLElement;
  /** Text eines Elements, geschützte Leerzeichen (Intl-Währungsformat) als normale Leerzeichen. */
  const plain = (value: string) => value.replace(/ /g, ' ').trim();
  const text = (selector: string) => plain(root().querySelector(selector)?.textContent ?? '');
  const label = () => text('.spendings-summary__label');
  const count = () => text('.spendings-summary__count');
  const sum = () => text('.spendings-summary__sum');
  const chip = () => root().querySelector('.spendings-filter__chip');
  const chipRemove = () => root().querySelector('.spendings-filter__chip-remove') as HTMLButtonElement;
  const filterButton = () => root().querySelector('.spendings-filter__button') as HTMLButtonElement;
  /** Aktiver Filter: Schaltfläche gefüllt statt umrandet. */
  const filterActive = () => filterButton().classList.contains('spendings-filter__button--active');
  const render = () => fixture.detectChanges();
  const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve));

  beforeEach(async () => {
    cartList = signal<Cart[]>([]);
    initCartList = signal<Cart[]>([]);
    const countSignal = computed(() => cartList().length);
    const sumSignal = computed(() => cartList().reduce((s, c) => s + (+c.amount), 0));
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot(), ScrollingModule, TranslateModule, RouterModule, ScientificCurrencyPipe],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList, cartUpdated: signal(0), sum: sumSignal, count: countSignal,
            getCartListByGroupId: jasmine.createSpy('load')
              .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.()),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: 'Anna' })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', { domains: { spendings: {
      total: 'Gesamt', filter: 'Filter', filter_active: 'Filter aktiv', remove_filter: 'Filter entfernen',
      count_one: '{{count}} Ausgabe', count_other: '{{count}} Ausgaben',
    } } });
    translate.use('de');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    fixture = TestBed.createComponent(CartlistPage);
    page = fixture.componentInstance;
    // schmales Gerät mit definierter Fläche (für die Höhenmessung im letzten Test); ion-page setzt in der App der
    // Router-Outlet: Seite als Flex-Spalte, ion-content erhält die Höhe unter dem Header
    root().classList.add('ion-page');
    root().style.cssText = 'position: fixed; top: 0; left: 0; width: 320px; height: 640px;';
    render();
    initCartList.set(alle);
    cartList.set(alle);
    render();
    await ionicReady(root());
  });

  it('ohne Filter: „Gesamt“, Anzahl und Summe der ganzen Liste, Filter-Schaltfläche nicht aktiv, kein Chip (AC 9)', () => {
    expect(label()).toBe('Gesamt');
    expect(count()).toBe('3 Ausgaben');
    expect(sum()).toBe('62,50 €');
    expect(filterActive()).toBeFalse();
    expect(chip()).toBeNull();
  });

  it('Schnellfilter Kategorie: Bezeichnung, gefilterte Werte, Chip mit Bezeichnung, Schaltfläche aktiv (AC 6, 9)', () => {
    page.onFilterCategory(edeka);
    render();

    expect(label()).toBe('Lebensmittel');
    expect(count()).toBe('2 Ausgaben');
    expect(sum()).toBe('52,00 €');
    expect(text('.spendings-filter__chip-label')).toBe('Lebensmittel');
    expect(filterActive()).toBeTrue();
  });

  it('Schnellfilter Mitglied: langer Name ungekürzt in Bezeichnung und Chip (Kürzung nur per CSS) (AC 6)', () => {
    const longName = 'Maximiliane Mustermann-Lüdenscheid';
    const baumarkt = aCart({ title: 'Baumarkt', amount: 7.25, userDto: aUser({ userName: longName }), categoryDto: drogerie });
    initCartList.set([...alle, baumarkt]);
    cartList.set([...alle, baumarkt]);

    page.onFilter('user', longName);
    render();

    expect(label()).toBe(longName);
    expect(text('.spendings-filter__chip-label')).toBe(longName);
    expect(count()).toBe('1 Ausgabe');
    expect(sum()).toBe('7,25 €');
  });

  it('Sheet-Filter mit einem Kriterium: „Gesamt“, Chip „Filter aktiv“, Singular bei einer Ausgabe (AC 7, 9)', () => {
    page.filterCarts({ ...noFilter, category: ['Drogerie'] });
    render();

    expect(label()).toBe('Gesamt');
    expect(count()).toBe('1 Ausgabe');
    expect(sum()).toBe('10,50 €');
    expect(text('.spendings-filter__chip-label')).toBe('Filter aktiv');
    expect(filterActive()).toBeTrue();
  });

  it('kombinierter Sheet-Filter (Kategorie und Mitglied): „Gesamt“ und gefilterte Werte (AC 8, 9)', () => {
    page.filterCarts({ ...noFilter, category: ['Lebensmittel'], userName: ['Anna'] });
    render();

    expect(label()).toBe('Gesamt');
    expect(count()).toBe('1 Ausgabe');
    expect(sum()).toBe('12,00 €');
    expect(text('.spendings-filter__chip-label')).toBe('Filter aktiv');
  });

  it('Schaltfläche und „×“ optisch 32px, Tippfläche 44px: 5px über und unter dem Element trifft der Tipp noch (AC 11)', async () => {
    page.onFilterCategory(edeka);
    render();
    // Chip und sein Icon entstehen erst jetzt: Hydration und Layout abwarten, bevor gemessen wird
    await ionicReady(root());
    await nextFrame();
    for (const element of [filterButton(), chipRemove()]) {
      const box = element.getBoundingClientRect();
      const x = box.left + box.width / 2;
      expect(box.height).toBe(32);
      expect(element.contains(document.elementFromPoint(x, box.top - 5))).withContext('oberhalb').toBeTrue();
      expect(element.contains(document.elementFromPoint(x, box.bottom + 5))).withContext('unterhalb').toBeTrue();
    }
  });

  it('Filter-Schaltfläche öffnet das bestehende Filter-Sheet (AC 10)', () => {
    const open = spyOn(page, 'openFilterSheet').and.resolveTo();

    filterButton().click();

    expect(open).toHaveBeenCalledTimes(1);
  });

  it('„×“ im Chip hebt den Filter auf: kein Chip, vollständige Liste (AC 11)', () => {
    page.onFilterCategory(edeka);
    render();
    expect(chipRemove().getAttribute('aria-label')).toBe('Filter entfernen');

    chipRemove().click();
    render();

    expect(chip()).toBeNull();
    expect(label()).toBe('Gesamt');
    expect(count()).toBe('3 Ausgaben');
    expect(sum()).toBe('62,50 €');
    expect(filterActive()).toBeFalse();
  });

  it('Filter ohne Treffer: 0 Ausgaben, 0,00 €, Chip bleibt; danach „×“ → vollständige Liste', () => {
    page.filterCarts({ ...noFilter, title: 'gibt es nicht' });
    render();
    expect(count()).toBe('0 Ausgaben');
    expect(sum()).toBe('0,00 €');
    expect(chip()).not.toBeNull();

    chipRemove().click();
    render();

    expect(count()).toBe('3 Ausgaben');
    expect(sum()).toBe('62,50 €');
  });

  it('große, negative und sehr große Beträge erscheinen vollständig (AC 5)', () => {
    const pipe = new ScientificCurrencyPipe();
    expect(plain(pipe.transform(999999.99, 'EUR'))).toBe('999.999,99 €');
    for (const amount of [999999.99, -999999.99, 123456789]) {
      cartList.set([aCart({ amount, userDto: sven, categoryDto: lebensmittel })]);
      render();
      expect(sum()).toBe(plain(pipe.transform(amount, 'EUR')));
    }
  });

  // DIV-77: Summe und Zeilen in der Währung der aktiven Gruppe; ein Wechsel der Gruppenwährung zeigt sofort das neue Symbol
  it('zeigt Summe und Beträge in der Währung der aktiven Gruppe (USD, danach CHF)', () => {
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG', currencyCode: 'USD' }));
    cartList.set([aCart({ amount: 12.5, userDto: sven, categoryDto: lebensmittel })]);
    render();
    const rowAmount = () => plain(root().querySelector('ion-item .cart-details h2:not(.titlestyle)')?.textContent ?? '');

    expect(sum()).toBe('12,50 $');
    expect(rowAmount()).toBe('12,50 $');

    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG', currencyCode: 'CHF' }));
    render();

    expect(sum()).toBe('12,50 CHF');
    expect(rowAmount()).toBe('12,50 CHF');
  });

  it('Zusammenfassung und Filterzeile liegen genau über der Listenspalte (schmal und breit, z. B. im Browser)', async () => {
    const columns = () => {
      const toolbarCol = root().querySelector('.spendings-toolbar ion-col')!.getBoundingClientRect();
      const listCol = root().querySelector('cdk-virtual-scroll-viewport')!.closest('ion-col')!.getBoundingClientRect();
      return { toolbarCol, listCol };
    };
    // Der md-Breakpoint von ion-col (768px) hängt an der Fensterbreite, nicht an der Fixture: dafür das Karma-iframe
    // vorübergehend verbreitern (wie im Drag-&-Drop-Block der Einkaufsliste die Höhe)
    const frame = window.frameElement as HTMLElement | null;
    const frameWidth = frame?.style.width ?? '';
    try {
      for (const width of [320, 1000]) {
        if (frame) frame.style.width = `${width + 100}px`;
        root().style.width = `${width}px`;
        // ion-col rechnet seine Breite bei window-resize neu und rendert im nächsten Frame
        window.dispatchEvent(new Event('resize'));
        render();
        await nextFrame();
        await nextFrame();
        const { toolbarCol, listCol } = columns();
        expect(toolbarCol.width).withContext(`${width}px`).toBeGreaterThan(0);
        expect(toolbarCol.left).withContext(`${width}px`).toBeCloseTo(listCol.left, 0);
        expect(toolbarCol.right).withContext(`${width}px`).toBeCloseTo(listCol.right, 0);
        if (width === 1000 && window.innerWidth >= 768) {
          // im Browser: halbe Breite, zentriert – nicht über die ganze Seite
          expect(toolbarCol.width).toBeLessThan(width * 0.6);
        }
      }
      expect(window.innerWidth).withContext('md-Breakpoint im Test erreicht').toBeGreaterThanOrEqual(768);
    } finally {
      if (frame) frame.style.width = frameWidth;
    }
  });

  it('Chip mit langer Bezeichnung lässt den Header wachsen → die Liste wird neu gemessen', async () => {
    const lang = aCategory({ name: 'Lebensmittel und Haushaltswaren vom Wochenmarkt' });
    const carts = [aCart({ amount: 3, userDto: sven, categoryDto: lang }), ...alle];
    initCartList.set(carts);
    cartList.set(carts);
    render();
    await nextFrame();
    render();
    const host = root().querySelector('.ion-content-scroll-host') as HTMLElement;
    const check = spyOn(page.viewport!, 'checkViewportSize').and.callThrough();
    const before = host.clientHeight;

    page.onFilterCategory(carts[0]);
    render();
    await nextFrame();
    await nextFrame();

    // Voraussetzung: bei 320 px Breite ändert der Chip tatsächlich die Höhe (Filterzeile bricht um)
    expect(host.clientHeight).toBeLessThan(before);
    expect(check).toHaveBeenCalled();
  });
});

// DIV-79 Phase 4: Mitgliedsname in der Liste ungekürzt im DOM, gekürzt nur per CSS-Ellipsis – Wiederholungs-Icon und
// Datum bleiben sichtbar, der Tipp auf den Namen filtert weiter nach dem Mitglied.
describe('CartlistPage – Mitgliedsname in der Liste (DIV-79)', () => {
  const ownName = 'Annabelle-Sophie Maximiliane Mustermann-Lüdenscheid';
  const otherName = 'Bartholomäus Friedrich Beispielmann-Oberhausen';
  const recurring = { recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, deleted: false };
  const own = aCart({ title: 'Miete', userDto: aUser({ userName: ownName }), categoryDto: aCategory({ name: 'Wohnen' }),
    datePurchased: '2026-11-15', ...recurring });
  const other = aCart({ title: 'Strom', userDto: aUser({ userName: otherName }), categoryDto: aCategory({ name: 'Wohnen' }),
    ...recurring });
  // aus der Gruppe entfernte Person (Phase 5): kursiv mit Zusatz „(entfernt)“
  const removedName = 'Konstantin Wilhelm Ausgetreten-Langenberg';
  const removed = aCart({ title: 'Kino', userDto: aUser({ userName: removedName }), categoryDto: aCategory({ name: 'Freizeit' }),
    deleted: true });

  let fixture: ComponentFixture<CartlistPage>;
  let page: CartlistPage;
  const root = () => fixture.nativeElement as HTMLElement;
  const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve));
  /** Zeile einer Ausgabe anhand ihres Titels. */
  const row = (title: string) => Array.from(root().querySelectorAll('ion-item-sliding'))
    .find(sliding => sliding.querySelector('h2.titlestyle')!.textContent!.trim() === title) as HTMLElement;

  beforeEach(async () => {
    const cartList = signal<Cart[]>([]);
    TestBed.configureTestingModule({
      declarations: [CartlistPage],
      imports: [IonicModule.forRoot(), ScrollingModule, TranslateModule, RouterModule, ScientificCurrencyPipe],
      providers: [
        ...provideServiceTesting(),
        {
          provide: CartService, useValue: {
            cartList, initCartList: signal<Cart[]>([]), cartUpdated: signal(0),
            sum: computed(() => 0), count: computed(() => cartList().length),
            getCartListByGroupId: jasmine.createSpy('load')
              .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.()),
          },
        },
        { provide: OverviewService, useValue: { overviewRefresh: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: ownName })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', { global: { user: { removed: 'Nutzer entfernt' } } });
    translate.use('de');
    TestBed.inject(GroupService).setActiveGroup(aGroup({ id: 3, name: 'WG' }));
    fixture = TestBed.createComponent(CartlistPage);
    page = fixture.componentInstance;
    // schmales Gerät, Seite als Flex-Spalte wie in der App (ion-page setzt dort der Router-Outlet)
    root().classList.add('ion-page');
    root().style.cssText = 'position: fixed; top: 0; left: 0; width: 320px; height: 640px;';
    fixture.detectChanges();
    cartList.set([own, other, removed]);
    fixture.detectChanges();
    // Virtual Scroll rendert die Zeilen erst nach dem nächsten Animation Frame
    await nextFrame();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await ionicReady(root());
    await nextFrame();
  });

  // DIV-81: Das Backend liefert den Kalendertag „YYYY-MM-DD“; die date-Pipe liest ihn als lokalen Tag – in jeder Zone.
  it('zeigt das Kaufdatum als Kalendertag an (AC 3)', () => {
    expect(row('Miete').querySelector('.cart-name-date')!.textContent!.trim()).toBe('15.11.2026');
  });

  for (const [kind, title, name] of [['eigener', 'Miete', ownName], ['fremder', 'Strom', otherName]]) {
    it(`${kind} langer Name: ungekürzt im DOM, per CSS gekürzt, Wiederholungs-Icon und Datum bleiben sichtbar`, () => {
      const user = row(title).querySelector('.cart-user') as HTMLElement;
      const icon = row(title).querySelector('.cart-repeat ion-icon[name="repeat"]') as HTMLElement;
      const date = row(title).querySelector('.cart-name-date') as HTMLElement;

      expect(user.textContent!.trim()).toBe(name);
      expect(user.textContent).not.toContain('...');
      // gekürzt wird nur die Darstellung (Ellipsis): Inhalt breiter als die sichtbare Box
      expect(user.scrollWidth).toBeGreaterThan(user.clientWidth);
      // Icon und Datum nicht verdrängt: Icon vollständig links vom Datum, Datum mit voller Breite
      expect(icon.getBoundingClientRect().width).toBeGreaterThan(0);
      expect(icon.getBoundingClientRect().right).toBeLessThanOrEqual(date.getBoundingClientRect().left);
      expect(date.scrollWidth).toBeLessThanOrEqual(date.clientWidth);
    });

    it(`${kind} langer Name: Tipp filtert weiterhin nach dem Mitglied`, () => {
      const filter = spyOn(page, 'onFilter');

      (row(title).querySelector('.cart-user') as HTMLElement).click();

      expect(filter).toHaveBeenCalledOnceWith('user', name);
    });
  }
  it('entfernte Person mit langem Namen: einzeilig per Ellipsis gekürzt wie aktive Mitglieder, Datum sichtbar (Phase 5)', () => {
    const user = row('Kino').querySelector('.cart-user') as HTMLElement;
    const date = row('Kino').querySelector('.cart-name-date') as HTMLElement;
    const activeUser = row('Strom').querySelector('.cart-user') as HTMLElement;

    expect(user.querySelector('em')!.textContent!.trim()).toBe(`${removedName} (entfernt)`);
    expect(user.scrollWidth).toBeGreaterThan(user.clientWidth);
    // eine Zeile wie bei aktiven Mitgliedern statt Umbruch
    expect(user.getBoundingClientRect().height).toBe(activeUser.getBoundingClientRect().height);
    expect(user.getBoundingClientRect().right).toBeLessThanOrEqual(date.getBoundingClientRect().left);
    expect(date.scrollWidth).toBeLessThanOrEqual(date.clientWidth);
  });
});
