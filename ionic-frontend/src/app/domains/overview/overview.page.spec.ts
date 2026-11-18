import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, UrlTree } from '@angular/router';
import { IonicModule } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { MonthNamePipe } from 'src/app/pipe/month-name.pipe';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { aGroup, aLoggedInUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { CartService } from '../cartlist/service/cart.service';
import { OverviewPage } from './overview.page';
import { OverviewService } from './service/overview.service';

// DIV-36: Lade-Effect der Übersicht (gleiche Struktur wie Shopping/Cartlist). Template ausgeblendet – geprüft wird nur
// die Effect-Logik, Diagramme und Darstellung sind nicht Gegenstand.
describe('OverviewPage – Neuladen nach Logout/Login', () => {
  let fixture: ComponentFixture<OverviewPage>;
  let groupService: GroupService;
  let session: BehaviorSubject<User | null>;
  let yearly: jasmine.Spy;
  let monthly: jasmine.Spy;

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    yearly = jasmine.createSpy('getSpendingsOverviewYearly')
      .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.());
    monthly = jasmine.createSpy('getSpendingsOverview');
    const overviewService = {
      spendingsOverview: signal(null), spendingsPerMonth: signal([]), spendingsTotalYearMonthly: signal(null),
      year: signal(null), spendingsOverviewYearly: signal(null), spendingsPerYear: signal([]),
      spendingsTotalYearYearly: signal(null), availableYears: signal([]),
      getSpendingsOverviewYearly: yearly, getSpendingsOverview: monthly,
    };
    TestBed.configureTestingModule({
      declarations: [OverviewPage],
      imports: [IonicModule.forRoot()],
      providers: [
        ...provideServiceTesting(),
        { provide: OverviewService, useValue: overviewService },
        { provide: CartService, useValue: { cartUpdated: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    TestBed.overrideTemplate(OverviewPage, '');
    groupService = TestBed.inject(GroupService);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture = TestBed.createComponent(OverviewPage);
    fixture.detectChanges();
    yearly.calls.reset();
    monthly.calls.reset();
  });

  it('Logout und Login mit derselben Gruppe ohne Change Detection dazwischen (abgehängte View): genau ein Ladezyklus', () => {
    session.next(null);
    session.next(aLoggedInUser({ id: 7 }));
    groupService.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
    fixture.detectChanges();

    expect(yearly).toHaveBeenCalledOnceWith(jasmine.objectContaining({ id: 1 }), false, jasmine.any(Function));
    expect(monthly).toHaveBeenCalledOnceWith(jasmine.any(Number), jasmine.objectContaining({ id: 1 }));
  });
});

// DIV-79 Phase 2: zweizeiliger Header mit echtem Template (der Test oben blendet das Template aus). Der Gruppenname steht
// ungekürzt im DOM (früher getShortGroupName, jetzt nur CSS-Ellipsis); rechts bleibt nur das Jahres-Menü.
describe('OverviewPage – Header (DIV-79)', () => {
  const longName = 'Wohngemeinschaft Musterstraße 12';
  let fixture: ComponentFixture<OverviewPage>;
  let groupService: GroupService;
  const root = () => fixture.nativeElement as HTMLElement;
  const groupButton = () => root().querySelector('ion-title button.page-title') as HTMLButtonElement;

  beforeEach(() => {
    const overviewService = {
      // Gesamtwerte leer statt null: das echte Template liest die Nutzerzeilen
      spendingsOverview: signal(null), spendingsPerMonth: signal([]),
      spendingsTotalYearMonthly: signal({ sumTotalYear: 0, spendingsTotalUser: [] }),
      year: signal(null), spendingsOverviewYearly: signal(null), spendingsPerYear: signal([]),
      spendingsTotalYearYearly: signal({ sumTotalYear: 0, spendingsTotalUser: [] }), availableYears: signal([2025]),
      getSpendingsOverviewYearly: jasmine.createSpy('getSpendingsOverviewYearly')
        .and.callFake((_group: unknown, _refresh: boolean, onComplete?: () => void) => onComplete?.()),
      getSpendingsOverview: jasmine.createSpy('getSpendingsOverview'),
    };
    TestBed.configureTestingModule({
      declarations: [OverviewPage],
      imports: [IonicModule.forRoot(), FormsModule, TranslateModule, RouterModule, ScientificCurrencyPipe, MonthNamePipe],
      providers: [
        ...provideServiceTesting(),
        { provide: OverviewService, useValue: overviewService },
        { provide: CartService, useValue: { cartUpdated: signal(0) } },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ id: 7 })) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    groupService.setActiveGroup(aGroup({ id: 1, name: longName }));
    fixture = TestBed.createComponent(OverviewPage);
    fixture.detectChanges();
  });

  it('Titel und darunter der volle Gruppenname im Gruppen-Button (AC 1, 2)', () => {
    expect(root().querySelector('ion-title .page-title__text')!.textContent!.trim()).toBe('domains.overview.title');
    expect(groupButton().querySelector('.page-title__group-name')!.textContent!.trim()).toBe(longName);
  });

  it('Tipp auf die Gruppe navigiert zur Gruppenübersicht', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);

    groupButton().click();

    expect(router.serializeUrl(navigate.calls.mostRecent().args[0] as UrlTree)).toBe('/groupoverview');
  });

  it('rechts nur noch das Jahres-Menü, kein Gruppenname (AC 3)', () => {
    const actions = root().querySelector('ion-buttons[slot="primary"]')!;
    expect(actions.textContent).not.toContain(longName);
    expect(actions.querySelectorAll(':scope > ion-button').length).toBe(1);
    expect(actions.querySelector('#popover-button')).not.toBeNull();
  });
});
