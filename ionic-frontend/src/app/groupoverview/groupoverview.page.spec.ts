import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { IonicModule, IonItemSliding } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from 'src/app/auth/auth.service';
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
