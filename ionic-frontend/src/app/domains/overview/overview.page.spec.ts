import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IonicModule } from '@ionic/angular';
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
