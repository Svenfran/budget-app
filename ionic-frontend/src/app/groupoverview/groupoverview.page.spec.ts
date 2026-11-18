import { Location } from '@angular/common';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { IonicModule, IonItemSliding } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { aGroup, aLoggedInUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { GroupoverviewPage } from './groupoverview.page';
import { GroupOverview } from './model/group-overview';

// DIV-72: Ein Klick auf eine Gruppe setzt sie nur aktiv (ohne Navigation); die Liste markiert die aktive Gruppe.
describe('GroupoverviewPage – Gruppe auswählen', () => {
  let fixture: ComponentFixture<GroupoverviewPage>;
  let page: GroupoverviewPage;
  let groupService: GroupService;

  const overview = (id: number, name: string): GroupOverview =>
    ({ id, name, dateCreated: new Date(2025, 0, 1), ownerName: 'Anna', memberCount: 1 });
  const haushalt = overview(1, 'Haushalt');
  const wg = overview(2, 'WG');
  const urlaub = overview(3, 'Urlaub');
  /** Item-Ersatz: `openAmount` > 0 = aufgeswiped */
  const slidingItem = (openAmount = 0) => {
    const item = jasmine.createSpyObj<IonItemSliding>('IonItemSliding', ['getOpenAmount', 'close']);
    item.getOpenAmount.and.resolveTo(openAmount);
    item.close.and.resolveTo();
    return item;
  };
  const markedNames = () => Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('ion-item.active-group'))
    .map(item => item.querySelector('h2')!.textContent!.trim());

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [GroupoverviewPage],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser()) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    spyOn(groupService, 'getGroupsForOverview');
    groupService.groupOverviewList.set([haushalt, wg, urlaub]);
    groupService.setActiveGroup(aGroup({ id: wg.id, name: wg.name }));
    fixture = TestBed.createComponent(GroupoverviewPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('markiert genau die aktive Gruppe', () => {
    expect(markedNames()).toEqual(['WG']);
    expect(fixture.nativeElement.querySelector('ion-item.active-group').getAttribute('aria-current')).toBe('true');
  });

  it('Klick setzt die Gruppe aktiv, verschiebt die Markierung und navigiert nicht', async () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    const navigateByUrl = spyOn(router, 'navigateByUrl');
    const back = spyOn(TestBed.inject(Location), 'back');

    await page.onSelectGroup(urlaub, slidingItem());
    fixture.detectChanges();

    expect(groupService.activeGroupId()).toBe(urlaub.id);
    expect(markedNames()).toEqual(['Urlaub']);
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(back).not.toHaveBeenCalled();
  });

  it('Klick auf die bereits aktive Gruppe setzt sie nicht erneut', async () => {
    const setActive = spyOn(groupService, 'setActiveGroup').and.callThrough();

    await page.onSelectGroup(wg, slidingItem());

    expect(setActive).not.toHaveBeenCalled();
    expect(groupService.activeGroupId()).toBe(wg.id);
  });

  it('ein aufgeswiptes Item wird nur geschlossen, die aktive Gruppe bleibt', async () => {
    const item = slidingItem(40);

    await page.onSelectGroup(haushalt, item);
    fixture.detectChanges();

    expect(item.close).toHaveBeenCalled();
    expect(groupService.activeGroupId()).toBe(wg.id);
    expect(markedNames()).toEqual(['WG']);
  });
});

// DIV-80: Nach dem Löschen der letzten Gruppe geht es erst nach erfolgreicher Antwort zu no-group.
describe('GroupoverviewPage – letzte Gruppe löschen (DIV-80)', () => {
  let page: GroupoverviewPage;
  let groupService: GroupService;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;
  const only: GroupOverview = { id: 1, name: 'Haushalt', dateCreated: new Date(2025, 0, 1), ownerName: 'Anna', memberCount: 0 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [GroupoverviewPage],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser()) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    http = TestBed.inject(HttpTestingController);
    spyOn(groupService, 'getGroupsForOverview');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    groupService.groupsSideNav.set([aGroup({ id: only.id, name: only.name })]);
    groupService.groupOverviewList.set([only]);
    page = TestBed.createComponent(GroupoverviewPage).componentInstance;
  });

  afterEach(() => http.verify());

  it('navigiert vor der Antwort nicht und nach Erfolg zu no-group', async () => {
    const done = page.deleteGroup(only);
    expect(navigate).not.toHaveBeenCalled();

    http.expectOne(req => req.url.endsWith('/api/groups/delete/1')).flush(null);
    await done;

    expect(groupService.hasNoGroups()).toBeTrue();
    expect(navigate).toHaveBeenCalledOnceWith(['/no-group'], { replaceUrl: true });
  });

  it('navigiert bei einem Fehler nicht und lässt die Gruppe stehen', async () => {
    const done = page.deleteGroup(only);
    http.expectOne(req => req.url.endsWith('/api/groups/delete/1'))
      .flush('User with Id 5 is not the owner of the Group', { status: 403, statusText: 'Forbidden' });
    await done;

    expect(navigate).not.toHaveBeenCalled();
    expect(groupService.hasNoGroups()).toBeFalse();
    expect(groupService.groupOverviewList()).toEqual([only]);
  });

  it('navigiert nach dem Löschen nicht, wenn weitere Gruppen bleiben', async () => {
    const other: GroupOverview = { ...only, id: 2, name: 'WG' };
    groupService.groupOverviewList.set([only, other]);

    const done = page.deleteGroup(only);
    http.expectOne(req => req.url.endsWith('/api/groups/delete/1')).flush(null);
    await done;

    expect(navigate).not.toHaveBeenCalled();
    expect(groupService.groupOverviewList()).toEqual([other]);
  });
});

// DIV-60: Die Übersicht lädt nur bei angemeldetem Nutzer, nicht bei der Logout-Emission (`null`).
describe('GroupoverviewPage – Laden der Übersicht (DIV-60)', () => {
  let session: BehaviorSubject<User | null>;
  let loadOverview: jasmine.Spy;
  let fixture: ComponentFixture<GroupoverviewPage>;

  const login = (id: number) => session.next(aLoggedInUser({ id }));
  const logout = () => session.next(null);

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    TestBed.configureTestingModule({
      declarations: [GroupoverviewPage],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    loadOverview = spyOn(TestBed.inject(GroupService), 'getGroupsForOverview');
    login(7);
    fixture = TestBed.createComponent(GroupoverviewPage);
    fixture.detectChanges();
  });

  it('lädt die Übersicht bei angemeldetem Nutzer', () => {
    expect(loadOverview).toHaveBeenCalledTimes(1);
  });

  it('startet bei Logout keinen Request', () => {
    logout();

    expect(loadOverview).toHaveBeenCalledTimes(1);
  });

  it('lädt nach erneutem Login wieder', () => {
    logout();
    login(8);

    expect(loadOverview).toHaveBeenCalledTimes(2);
  });

  it('lädt nach dem Verlassen der Seite bei einem Login nicht mehr (Abo beendet)', () => {
    fixture.destroy();

    login(8);

    expect(loadOverview).toHaveBeenCalledTimes(1);
  });
});
