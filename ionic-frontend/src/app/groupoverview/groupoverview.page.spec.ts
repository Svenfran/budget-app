import { Location } from '@angular/common';
import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AlertController, IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { ionicReady } from 'src/testing/ionic-ready';
import { aGroup, aGroupOverview, aLoggedInUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { GroupFormSheetComponent } from './group-form-sheet/group-form-sheet.component';
import { GroupoverviewPage } from './groupoverview.page';
import { MembersSheetComponent } from './members-sheet/members-sheet.component';
import { GroupOverview } from './model/group-overview';

// DIV-72: Ein Klick auf eine Gruppe setzt sie nur aktiv (ohne Navigation); die Liste markiert die aktive Gruppe.
describe('GroupoverviewPage – Gruppe auswählen', () => {
  let fixture: ComponentFixture<GroupoverviewPage>;
  let page: GroupoverviewPage;
  let groupService: GroupService;

  const overview = (id: number, name: string): GroupOverview =>
    aGroupOverview({ id, name });
  const haushalt = overview(1, 'Haushalt');
  const wg = overview(2, 'WG');
  const urlaub = overview(3, 'Urlaub');
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

  it('Klick setzt die Gruppe aktiv, verschiebt die Markierung und navigiert nicht', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    const navigateByUrl = spyOn(router, 'navigateByUrl');
    const back = spyOn(TestBed.inject(Location), 'back');

    page.onSelectGroup(urlaub);
    fixture.detectChanges();

    expect(groupService.activeGroupId()).toBe(urlaub.id);
    expect(markedNames()).toEqual(['Urlaub']);
    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(back).not.toHaveBeenCalled();
  });

  it('Klick auf die bereits aktive Gruppe setzt sie nicht erneut', () => {
    const setActive = spyOn(groupService, 'setActiveGroup').and.callThrough();

    page.onSelectGroup(wg);

    expect(setActive).not.toHaveBeenCalled();
    expect(groupService.activeGroupId()).toBe(wg.id);
  });

  it('„+“ im Header öffnet das Sheet „Gruppe hinzufügen“, „Gruppe bearbeiten“ das Sheet mit der Gruppe (DIV-80)', async () => {
    // Modal nur als Stub: ein echtes Overlay würde spätere Layout-Tests verdecken
    const modal = { present: jasmine.createSpy('present').and.resolveTo(), querySelector: () => null };
    const create = spyOn(TestBed.inject(ModalController), 'create').and.resolveTo(modal as unknown as HTMLIonModalElement);

    (fixture.nativeElement.querySelector('ion-header ion-button') as HTMLElement).click();
    await create.calls.mostRecent().returnValue;
    await page.onUpdateGroup(wg);

    const [createOptions, editOptions] = create.calls.allArgs().map(args => args[0]);
    expect(createOptions.component).toBe(GroupFormSheetComponent);
    expect(createOptions.componentProps).toEqual(jasmine.objectContaining({ mode: 'create', group: undefined }));
    expect(editOptions.componentProps).toEqual(jasmine.objectContaining({ mode: 'edit', group: wg }));
    expect(modal.present).toHaveBeenCalledTimes(2);
  });

  it('hat keine Sliding-Aktionen mehr, sondern je Gruppe einen ⋮-Button (DIV-80)', () => {
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('ion-item-sliding')).toBeNull();
    expect(host.querySelectorAll('ion-item.group-item .group-menu-button').length).toBe(3);
  });
});

// DIV-80: ⋮-Menü je Gruppe mit Admin-Erkennung per User-ID (ein gleichnamiges Mitglied ist kein Admin)
describe('GroupoverviewPage – Gruppenmenü (DIV-80)', () => {
  let fixture: ComponentFixture<GroupoverviewPage>;
  let page: GroupoverviewPage;
  let groupService: GroupService;
  const me = aLoggedInUser({ name: 'Anna' });
  // fremde Gruppe, deren Admin zufällig genauso heißt wie der Nutzer
  const fremd = aGroupOverview({ id: 1, name: 'Fremd', ownerName: 'Anna', ownerId: me.id + 1000 });
  const eigene = aGroupOverview({ id: 2, name: 'Eigene', ownerName: 'Anna', ownerId: me.id });

  const host = () => fixture.nativeElement as HTMLElement;
  const popover = () => document.querySelector('ion-popover.group-menu') as HTMLIonPopoverElement | null;
  const menuepunkte = () => Array.from(popover()?.querySelectorAll('ion-item') ?? []) as HTMLIonItemElement[];
  const punkt = (cls: string) => popover()!.querySelector(`ion-item.${cls}`) as HTMLIonItemElement;

  /** Tippt auf ⋮ der Gruppe und wartet, bis Ionic das Popover samt Inhalt dargestellt hat (Muster Einkaufsliste). */
  const menuOeffnen = async (index: number) => {
    (host().querySelectorAll('.group-menu-button')[index] as HTMLElement).click();
    fixture.detectChanges();
    for (let i = 0; i < 100 && menuepunkte().length === 0; i++) {
      await new Promise(resolve => setTimeout(resolve, 20));
      fixture.detectChanges();
    }
    expect(menuepunkte().length).toBeGreaterThan(0);
  };

  // Ionic hängt das dargestellte Popover außerhalb der Fixture ein: schließen und entfernen
  const aufraeumen = async () => {
    for (const el of Array.from(document.querySelectorAll('ion-popover.group-menu')) as HTMLIonPopoverElement[]) {
      await el.dismiss().catch(() => undefined);
      el.remove();
    }
    for (const el of Array.from(document.querySelectorAll('ion-alert')) as HTMLIonAlertElement[]) {
      await el.dismiss().catch(() => undefined);
      el.remove();
    }
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      declarations: [GroupoverviewPage],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: authServiceStub(me) },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    groupService = TestBed.inject(GroupService);
    spyOn(groupService, 'getGroupsForOverview');
    groupService.groupOverviewList.set([fremd, eigene]);
    groupService.setActiveGroup(aGroup({ id: fremd.id, name: fremd.name }));
    fixture = TestBed.createComponent(GroupoverviewPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    await ionicReady(host());
  });

  afterEach(aufraeumen);

  it('Admin sieht „Mitglieder verwalten“, „Gruppe bearbeiten“ und „Gruppe löschen“', async () => {
    await menuOeffnen(1);

    expect(menuepunkte().map(item => item.className.match(/menu-\w+/)![0]))
      .toEqual(['menu-members', 'menu-edit', 'menu-delete']);
    expect(menuepunkte().map(item => item.querySelector('h3')!.textContent!.trim()))
      .toEqual(['groups.menu.members', 'groups.menu.edit', 'groups.menu.delete']);
  });

  it('Mitglied (auch bei gleichem Namen wie der Admin) sieht nur „Mitglieder verwalten“', async () => {
    await menuOeffnen(0);

    expect(menuepunkte().map(item => item.className.match(/menu-\w+/)![0])).toEqual(['menu-members']);
  });

  it('Tipp auf ⋮ wählt die Gruppe nicht aus', async () => {
    const setActive = spyOn(groupService, 'setActiveGroup').and.callThrough();

    await menuOeffnen(1);

    expect(setActive).not.toHaveBeenCalled();
    expect(groupService.activeGroupId()).toBe(fremd.id);
  });

  it('„Gruppe bearbeiten“ öffnet die Bearbeitung genau dieser Gruppe', async () => {
    const update = spyOn(page, 'onUpdateGroup');

    await menuOeffnen(1);
    punkt('menu-edit').click();

    expect(update).toHaveBeenCalledOnceWith(eigene);
  });

  it('„Mitglieder verwalten“ öffnet das Mitglieder-Sheet genau dieser Gruppe in der Listenansicht', async () => {
    // Modal nur als Stub: ein echtes Overlay würde spätere Layout-Tests verdecken
    const modal = { present: jasmine.createSpy('present').and.resolveTo() };
    const create = spyOn(TestBed.inject(ModalController), 'create').and.resolveTo(modal as unknown as HTMLIonModalElement);

    await menuOeffnen(1);
    punkt('menu-members').click();
    await create.calls.mostRecent().returnValue;
    await new Promise(resolve => setTimeout(resolve));

    const options = create.calls.mostRecent().args[0];
    expect(options.component).toBe(MembersSheetComponent);
    expect(options.componentProps).toEqual(jasmine.objectContaining({ groupId: eigene.id, groupName: 'Eigene' }));
    expect(options.componentProps!['view']()).toBe('list');
    expect(modal.present).toHaveBeenCalled();
  });

  it('„Gruppe löschen“ öffnet direkt die Sicherheitsabfrage mit Gruppenname; gelöscht wird nur über „Löschen“', async () => {
    const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
    // Alert nur als Stub: ein echtes Overlay würde nach dem Test noch erscheinen und Layout-Tests anderer Specs verdecken
    const present = jasmine.createSpy('present').and.resolveTo();
    const create = spyOn(TestBed.inject(AlertController), 'create')
      .and.resolveTo({ present } as unknown as HTMLIonAlertElement);
    const deleteGroup = spyOn(groupService, 'deleteGroup').and.resolveTo(true);

    await menuOeffnen(1);
    punkt('menu-delete').click();
    expect(create).toHaveBeenCalledTimes(1);
    await create.calls.mostRecent().returnValue;
    await new Promise(resolve => setTimeout(resolve));
    expect(present).toHaveBeenCalled();

    expect(translate).toHaveBeenCalledWith('alerts.group.delete.message', { groupName: 'Eigene' });
    const buttons = create.calls.mostRecent().args[0]!.buttons as { role?: string; cssClass?: string; handler?: () => void }[];
    expect(buttons.map(b => b.role)).toEqual(['cancel', 'destructive']);
    expect(buttons[1].cssClass).toBe('alert-button-danger');
    expect(buttons[0].handler).toBeUndefined();
    expect(deleteGroup).not.toHaveBeenCalled();

    buttons[1].handler!();
    expect(deleteGroup).toHaveBeenCalledOnceWith(eigene);
  });
});

// DIV-80: Lange Namen verbreitern die Zeile nicht, der ⋮-Button behält seine Touch-Fläche.
describe('GroupoverviewPage – Layout langer Namen (DIV-80)', () => {
  let fixture: ComponentFixture<GroupoverviewPage>;
  const WIDTH = 320;

  beforeEach(async () => {
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
    const groupService = TestBed.inject(GroupService);
    spyOn(groupService, 'getGroupsForOverview');
    groupService.groupOverviewList.set([aGroupOverview({
      name: 'Eine sehr lange Gruppenbezeichnung ohne Ende, die mehrere Zeilen braucht',
      ownerName: 'Maximiliane-Alexandra von Hohenzollern-Sigmaringen',
      memberCount: 12,
    })]);
    fixture = TestBed.createComponent(GroupoverviewPage);
    const root = fixture.nativeElement as HTMLElement;
    root.classList.add('ion-page');
    Object.assign(root.style, { position: 'fixed', top: '0', left: '0', width: `${WIDTH}px`, height: '640px' });
    fixture.detectChanges();
    await ionicReady(root);
    await new Promise(resolve => requestAnimationFrame(resolve));
  });

  it('kein horizontaler Überlauf; Admin-Name vollständig und umgebrochen; ⋮ mindestens 44 px', () => {
    const root = fixture.nativeElement as HTMLElement;
    const item = root.querySelector('ion-item.group-item') as HTMLElement;
    const admin = root.querySelector('.admin') as HTMLElement;
    const button = root.querySelector('.group-menu-button') as HTMLElement;

    expect(item.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
    expect(button.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
    expect(button.getBoundingClientRect().width).toBeGreaterThanOrEqual(44);
    expect(button.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
    expect(root.querySelector('.admin-name')!.textContent!.trim()).toBe('Maximiliane-Alexandra von Hohenzollern-Sigmaringen');
    expect(admin.scrollWidth).toBeLessThanOrEqual(admin.clientWidth + 1);
    expect(admin.getBoundingClientRect().height).toBeGreaterThan(root.querySelector('.group-members')!.getBoundingClientRect().height * 1.5);
  });
});

// DIV-80 (Gerätetest Nutzer): Bei großer Schrift überlagerten sich „Admin: …“ und „Mitglieder: N“, der Admin-Name war
// nicht mehr zu sehen. Jetzt steht die Mitgliederzahl immer in einer eigenen Zeile (einheitlich für alle Einträge).
describe('GroupoverviewPage – Detailzeile bei großer Schrift (DIV-80)', () => {
  let fixture: ComponentFixture<GroupoverviewPage>;
  let width = 320;
  // ion-col (size-md="6") richtet sich nach der Fensterbreite: iframe auf Handybreite (unter md) wie in cartlist.page.spec
  const frame = window.frameElement as HTMLElement | null;
  const frameWidth = frame?.style.width ?? '';

  const rect = (sel: string) => (fixture.nativeElement as HTMLElement).querySelector(sel)!.getBoundingClientRect();
  const overlap = (a: DOMRect, b: DOMRect) =>
    a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;

  async function render(textScale: number, ownerName = 'Sven Beispiel', fixtureWidth = 320) {
    width = fixtureWidth;
    // Android-Schriftgröße als Root-Schrift nachgestellt (Codex F-004: am Dokument, nicht am Fixture-Root)
    document.documentElement.style.fontSize = `${textScale * 100}%`;
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
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('de', { groups: { group: { members: 'Mitglieder:' } } }, true);
    translate.use('de');
    const groupService = TestBed.inject(GroupService);
    spyOn(groupService, 'getGroupsForOverview');
    groupService.groupOverviewList.set([aGroupOverview({ name: 'Haushalt', ownerName, memberCount: 12 })]);
    fixture = TestBed.createComponent(GroupoverviewPage);
    const root = fixture.nativeElement as HTMLElement;
    root.classList.add('ion-page');
    Object.assign(root.style, { position: 'fixed', top: '0', left: '0', width: `${width}px`, height: '640px' });
    if (frame) frame.style.width = `${width + 100}px`;
    window.dispatchEvent(new Event('resize'));
    fixture.detectChanges();
    await ionicReady(root);
    await new Promise(resolve => requestAnimationFrame(resolve));
    await new Promise(resolve => requestAnimationFrame(resolve));
  }

  afterEach(() => {
    document.documentElement.style.fontSize = '';
    if (frame) frame.style.width = frameWidth;
    window.dispatchEvent(new Event('resize'));
  });

  it('normale Schrift, kurzer Name: trotzdem zwei Zeilen wie jeder Eintrag, Name vollständig', async () => {
    await render(1, 'Anna', 412);

    const admin = rect('.admin');
    const members = rect('.group-members');
    expect(members.top).toBeGreaterThanOrEqual(admin.bottom - 1);
    expect(Math.abs(admin.left - members.left)).toBeLessThan(1);
    // einzeilig: so hoch wie die Mitgliederzeile
    expect(admin.height).toBeLessThan(members.height * 1.5);
  });

  it('normale Schrift, langer Name: bricht wie der Gruppenname um, vollständig lesbar, kein Überlauf', async () => {
    await render(1, 'Maximiliane-Alexandra von Hohenzollern-Sigmaringen', 320);

    const admin = (fixture.nativeElement as HTMLElement).querySelector('.admin') as HTMLElement;
    expect(rect('.group-members').top).toBeGreaterThanOrEqual(rect('.admin').bottom - 1);
    expect(rect('.admin').height).toBeGreaterThan(rect('.group-members').height * 1.5);
    expect(admin.scrollWidth).toBeLessThanOrEqual(admin.clientWidth + 1);
    expect(rect('.admin').right).toBeLessThanOrEqual(rect('.group-details').right + 0.5);
  });

  it('200 % Schrift: Mitgliederzahl in eigener Zeile, nichts überlagert, Name vollständig lesbar', async () => {
    await render(2);
    // Nachweis, dass die Vergrößerung wirkt (13 px bei 100 %)
    expect(parseFloat(getComputedStyle((fixture.nativeElement as HTMLElement).querySelector('.group-details')!).fontSize))
      .toBeGreaterThanOrEqual(25);

    const admin = rect('.admin');
    const members = rect('.group-members');
    const adminEl = (fixture.nativeElement as HTMLElement).querySelector('.admin') as HTMLElement;
    expect(overlap(admin, members)).toBeFalse();
    expect(members.top).toBeGreaterThanOrEqual(admin.bottom - 1);
    // Name vollständig lesbar (umgebrochen statt abgeschnitten)
    expect(adminEl.scrollWidth).toBeLessThanOrEqual(adminEl.clientWidth + 1);
    expect(adminEl.textContent).toContain('Sven Beispiel');
    expect(members.right).toBeLessThanOrEqual(width + 0.5);
    expect((fixture.nativeElement as HTMLElement).querySelector('ion-item.group-item')!.getBoundingClientRect().right)
      .toBeLessThanOrEqual(width + 0.5);
  });
});

// DIV-80: Nach dem Löschen der letzten Gruppe geht es erst nach erfolgreicher Antwort zu no-group.
describe('GroupoverviewPage – letzte Gruppe löschen (DIV-80)', () => {
  let page: GroupoverviewPage;
  let groupService: GroupService;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;
  const only: GroupOverview = aGroupOverview({ id: 1, name: 'Haushalt', memberCount: 0 });

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
