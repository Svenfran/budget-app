import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ScrollingModule } from '@angular/cdk/scrolling';
import { Router, RouterModule } from '@angular/router';
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
    datePurchased: '2025-06-10' as unknown as Date });
  const partnerCart = aCart({ id: 13, title: 'Ausgleichszahlung von Anna', amount: -30, userDto: ben, settlementId: 70,
    datePurchased: '2025-06-10' as unknown as Date });
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

    erfolgreichGeaendert({ ...kino, datePurchased: new Date(2025, 5, 1) });

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
    erfolgreichGeaendert({ ...kino, datePurchased: new Date(2025, 5, 1) });

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
