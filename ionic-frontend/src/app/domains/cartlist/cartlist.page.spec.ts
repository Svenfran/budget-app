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
import { aGroup, aLoggedInUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { OverviewService } from '../overview/service/overview.service';
import { Cart } from './model/cart';
import { CartlistPage } from './cartlist.page';
import { CartService } from './service/cart.service';
import { ExportDialogComponent } from './export-dialog/export-dialog.component';

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
    expect(options.cssClass).toBe('export-sheet');
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
