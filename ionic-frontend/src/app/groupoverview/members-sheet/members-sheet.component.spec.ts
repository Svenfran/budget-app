import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { AlertController, IonicModule, ModalController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { Init } from 'src/app/constants/default-values';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { LoadOptions } from 'src/app/model/load-options';
import { GroupService } from 'src/app/service/group.service';
import { ionicReady } from 'src/testing/ionic-ready';
import { aGroupOverview, aLoggedInUser, aUser } from 'src/testing/test-data';
import { authServiceStub, provideServiceTesting } from 'src/testing/test-providers';
import { GROUP_GONE_ROLE } from '../group-sheet-roles';
import { GroupOverview } from '../model/group-overview';
import { MEMBERS_SHEET_LEFT_ROLE, MembersSheetComponent, MembersSheetView } from './members-sheet.component';

describe('MembersSheetComponent (DIV-80)', () => {
  let modalCtrl: jasmine.SpyObj<ModalController>;
  let alertRole: string;
  let alertCreate: jasmine.Spy;
  let groupMembers: WritableSignal<GroupMembers>;
  let overview: WritableSignal<GroupOverview[]>;
  let session: BehaviorSubject<User | null>;
  let loads: { groupId: number; options: LoadOptions; resolve: (ok: boolean) => void }[];
  let changeGroupOwner: jasmine.Spy;
  let removeMemberFromGroup: jasmine.Spy;
  let addMemberToGroup: jasmine.Spy;

  const MODAL_ID = 'members-sheet-3';
  const anna = aLoggedInUser({ name: 'Anna' });
  const ben = aUser({ userName: 'Ben' });
  const cleo = aUser({ userName: 'Cleo' });
  const WG = 7;
  const URLAUB = 8;
  const wgMembers = (overrides: Partial<GroupMembers> = {}): GroupMembers =>
    ({ id: WG, name: 'WG', ownerId: anna.id, ownerName: 'Anna', members: [ben, cleo], ...overrides });

  /** Antwort auf einen Ladeaufruf wie GroupService: übernimmt nur, solange `isCurrent()` gilt. */
  const answer = (index: number, members: GroupMembers) => {
    const load = loads[index];
    if (load.options.isCurrent?.() ?? true) {
      groupMembers.set(members);
      load.resolve(true);
    } else {
      load.resolve(false);
    }
  };
  const fail = (index: number) => {
    const load = loads[index];
    if (load.options.isCurrent?.() ?? true) groupMembers.set(Init.DEFAULT_GROUP_MEMBERS);
    load.resolve(false);
  };
  const settle = () => new Promise(resolve => setTimeout(resolve));

  function create(groupId = WG, groupName = 'WG', view: MembersSheetView = 'list') {
    const fixture = TestBed.createComponent(MembersSheetComponent);
    const component = fixture.componentInstance;
    Object.assign(component, { modalId: MODAL_ID, groupId, groupName, saving: signal(false), view: signal(view) });
    fixture.detectChanges();
    return { fixture, component, el: fixture.nativeElement as HTMLElement };
  }
  async function loaded(members = wgMembers(), groupId = WG) {
    const sheet = create(groupId);
    answer(loads.length - 1, members);
    await settle();
    sheet.fixture.detectChanges();
    return sheet;
  }
  const names = (el: HTMLElement) => Array.from(el.querySelectorAll('.member-name')).map(n => n.textContent!.trim());
  const menuButtons = (el: HTMLElement) => el.querySelectorAll('.member-menu-button').length;

  beforeEach(() => {
    modalCtrl = jasmine.createSpyObj<ModalController>('ModalController', ['dismiss']);
    modalCtrl.dismiss.and.resolveTo(true);
    alertRole = 'confirm';
    alertCreate = jasmine.createSpy('create').and.callFake(async () => ({
      present: jasmine.createSpy('present').and.resolveTo(),
      onDidDismiss: () => Promise.resolve({ role: alertRole }),
    }));
    groupMembers = signal(Init.DEFAULT_GROUP_MEMBERS);
    overview = signal<GroupOverview[]>([aGroupOverview({ id: WG, name: 'WG' }), aGroupOverview({ id: URLAUB, name: 'Urlaub' })]);
    session = new BehaviorSubject<User | null>(anna);
    loads = [];
    changeGroupOwner = jasmine.createSpy('changeGroupOwner').and.resolveTo(true);
    removeMemberFromGroup = jasmine.createSpy('removeMemberFromGroup').and.resolveTo(true);
    addMemberToGroup = jasmine.createSpy('addMemberToGroup').and.resolveTo({ ok: true });
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    TestBed.configureTestingModule({
      declarations: [MembersSheetComponent],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: ModalController, useValue: modalCtrl },
        { provide: AlertController, useValue: { create: alertCreate } },
        { provide: AuthService, useValue: auth },
        {
          provide: GroupService, useValue: {
            groupMembers,
            groupMembersWithOwner: computed(() =>
              [{ id: groupMembers().ownerId, userName: groupMembers().ownerName }, ...groupMembers().members]),
            groupOverviewList: overview,
            sessionGuard: () => () => true,
            getGroupMembers: jasmine.createSpy('getGroupMembers').and.callFake((groupId: number, options: LoadOptions) =>
              new Promise<boolean>(resolve => loads.push({ groupId, options, resolve }))),
            changeGroupOwner, removeMemberFromGroup, addMemberToGroup,
          },
        },
      ],
    });
  });

  describe('Laden', () => {
    it('lädt die Mitglieder der Gruppe und zeigt den Admin zuerst mit Kennzeichnung (AC 5, 6)', async () => {
      const { el } = await loaded();

      expect(loads.map(l => l.groupId)).toEqual([WG]);
      expect(names(el)).toEqual(['Anna', 'Ben', 'Cleo']);
      const badges = Array.from(el.querySelectorAll('.member-row')).map(row => !!row.querySelector('.admin-badge'));
      expect(badges).toEqual([true, false, false]);
      expect(el.querySelector('.admin-badge')!.textContent!.trim()).toBe('groups.members_sheet.admin_badge');
    });

    it('zeigt Mitglieder einer vorher geöffneten Gruppe nicht: Ladezustand ohne Zeilen und Aktionen (Codex F-003)', () => {
      groupMembers.set(wgMembers());

      const { el } = create(URLAUB, 'Urlaub');

      expect(el.querySelector('ion-spinner')).not.toBeNull();
      expect(names(el)).toEqual([]);
      expect(menuButtons(el)).toBe(0);
      expect(el.querySelector('.btn-add-member')).toBeNull();
    });

    it('Antwort eines geschlossenen Sheets überschreibt die Liste eines neueren nicht (Codex F-003)', async () => {
      const a = create(WG);
      a.fixture.destroy();
      const b = create(URLAUB, 'Urlaub');
      const urlaubMembers = wgMembers({ id: URLAUB, name: 'Urlaub', members: [cleo] });

      answer(1, urlaubMembers);
      answer(0, wgMembers());
      await settle();
      b.fixture.detectChanges();

      expect(groupMembers()).toBe(urlaubMembers);
      expect(names(b.el)).toEqual(['Anna', 'Cleo']);
    });

    it('Ladefehler: Spinner endet, Meldung mit „Erneut versuchen“, keine Mitglieder und Aktionen (Codex F-005)', async () => {
      groupMembers.set(wgMembers({ id: URLAUB }));
      const { fixture, el } = create(WG);

      fail(0);
      await settle();
      fixture.detectChanges();

      expect(el.querySelector('ion-spinner')).toBeNull();
      expect(el.querySelector('.load-error p')!.textContent!.trim()).toBe('groups.members_sheet.load_error');
      expect(names(el)).toEqual([]);
      expect(menuButtons(el)).toBe(0);

      (el.querySelector('.btn-retry') as HTMLElement).click();
      fixture.detectChanges();
      expect(el.querySelector('ion-spinner')).not.toBeNull();
      expect(loads.length).toBe(2);

      answer(1, wgMembers());
      await settle();
      fixture.detectChanges();
      expect(names(el)).toEqual(['Anna', 'Ben', 'Cleo']);
      expect(el.querySelector('.load-error')).toBeNull();
    });

    it('lädt bei Logout nicht und nach dem Schließen bei einem Login nicht mehr (Abo endet, wie DIV-60)', () => {
      const { fixture } = create();
      session.next(null);
      expect(loads.length).toBe(1);

      fixture.destroy();
      session.next(aLoggedInUser());
      expect(loads.length).toBe(1);
    });
  });

  describe('Rechte und Aktionen', () => {
    it('Admin: Button „Mitglied hinzufügen“ und ⋮ bei allen außer der Admin-Zeile (AC 7, 8)', async () => {
      const { el } = await loaded();

      expect(el.querySelector('.btn-add-member')).not.toBeNull();
      const withMenu = Array.from(el.querySelectorAll('.member-row')).map(row => !!row.querySelector('.member-menu-button'));
      expect(withMenu).toEqual([false, true, true]);
    });

    it('Mitglied: kein „Mitglied hinzufügen“, ⋮ nur in der eigenen Zeile', async () => {
      session.next(aLoggedInUser({ id: ben.id, name: 'Ben' }));
      const { el } = await loaded();

      expect(el.querySelector('.btn-add-member')).toBeNull();
      const withMenu = Array.from(el.querySelectorAll('.member-row')).map(row => !!row.querySelector('.member-menu-button'));
      expect(withMenu).toEqual([false, true, false]);
    });

    it('„Zum Admin machen“ erst nach Bestätigung; danach hat der Ex-Admin keine Aktionen mehr (E1, AC 8)', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      const { fixture, component, el } = await loaded();

      alertRole = 'cancel';
      await component.makeAdmin(ben);
      expect(changeGroupOwner).not.toHaveBeenCalled();

      alertRole = 'confirm';
      changeGroupOwner.and.callFake(async () => {
        groupMembers.set(wgMembers({ ownerId: ben.id, ownerName: 'Ben', members: [{ id: anna.id, userName: 'Anna' }, cleo] }));
        return true;
      });
      await component.makeAdmin(ben);
      fixture.detectChanges();

      expect(translate).toHaveBeenCalledWith('alerts.group.change_owner.message', { memberName: 'Ben', group: 'WG' });
      expect(changeGroupOwner).toHaveBeenCalledOnceWith({ newOwner: { id: ben.id, userName: 'Ben' }, groupId: WG });
      expect(names(el)).toEqual(['Ben', 'Anna', 'Cleo']);
      expect(menuButtons(el)).toBe(1); // nur noch die eigene Zeile (Austritt)
      expect(el.querySelector('.btn-add-member')).toBeNull();
    });

    it('„Mitglied entfernen“ nach dem bisherigen Bestätigungs-Alert; das Sheet bleibt offen (AC 7)', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      const { component } = await loaded();

      alertRole = 'cancel';
      await component.removeMember(cleo);
      expect(removeMemberFromGroup).not.toHaveBeenCalled();

      alertRole = 'confirm';
      await component.removeMember(cleo);

      expect(translate).toHaveBeenCalledWith('alerts.group.remove_member.message', { memberName: 'Cleo', group: 'WG' });
      expect(removeMemberFromGroup).toHaveBeenCalledOnceWith({ id: WG, name: 'WG', member: cleo });
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    it('eigener Austritt schließt das Sheet nach Erfolg, bei Fehler bleibt es offen', async () => {
      session.next(aLoggedInUser({ id: ben.id, name: 'Ben' }));
      const { component } = await loaded();

      removeMemberFromGroup.and.resolveTo(false);
      await component.removeMember(ben);
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();

      removeMemberFromGroup.and.resolveTo(true);
      await component.removeMember(ben);
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, MEMBERS_SHEET_LEFT_ROLE, MODAL_ID);
    });

    it('Live-Event: neue Mitglieder erscheinen in der offenen Liste (E5)', async () => {
      const { fixture, el } = await loaded();

      groupMembers.set(wgMembers({ members: [ben, cleo, aUser({ userName: 'Dora' })] }));
      fixture.detectChanges();

      expect(names(el)).toEqual(['Anna', 'Ben', 'Cleo', 'Dora']);
    });
  });

  describe('Mitglied hinzufügen (selbes Sheet)', () => {
    it('wechselt im selben Sheet zur Eingabe und mit „Abbrechen“ zurück, ohne neues Modal (AC 9, 10, 11)', async () => {
      const { fixture, component, el } = await loaded();

      (el.querySelector('.btn-add-member') as HTMLElement).click();
      fixture.detectChanges();
      expect(component.view()).toBe('add');
      expect(el.querySelector('h2')!.textContent!.trim()).toBe('groups.members_sheet.add_title');
      expect(el.querySelector('.member-email-input')).not.toBeNull();
      expect(el.querySelector('.member-list')).toBeNull();

      (el.querySelector('.btn-cancel') as HTMLElement).click();
      fixture.detectChanges();
      expect(component.view()).toBe('list');
      expect(names(el)).toEqual(['Anna', 'Ben', 'Cleo']);
      expect(addMemberToGroup).not.toHaveBeenCalled();
      expect(modalCtrl.dismiss).not.toHaveBeenCalled();
    });

    it('ungültige E-Mail: Feldfehler, kein Request (A4)', async () => {
      const { fixture, component, el } = await loaded();
      component.openAdd();
      component.email.setValue('kein-mail');

      await component.submitAdd();
      fixture.detectChanges();

      expect(addMemberToGroup).not.toHaveBeenCalled();
      expect(el.querySelector('.error-email')!.textContent!.trim()).toBe('groups.members_sheet.email_invalid');
      expect(component.view()).toBe('add');
    });

    it('Erfolg: getrimmte E-Mail gesendet, zurück zur Liste (AC 11)', async () => {
      const { component } = await loaded();
      component.openAdd();
      component.email.setValue('  dora@example.com ');

      await component.submitAdd();

      expect(addMemberToGroup).toHaveBeenCalledOnceWith({ id: WG, name: 'WG', newMemberEmail: 'dora@example.com' });
      expect(component.view()).toBe('list');
      expect(component.saving()).toBeFalse();
    });

    it('Server-Fehler: Meldung unter dem Feld, Eingabe bleibt; neue Eingabe blendet sie aus (E6)', async () => {
      const { fixture, component, el } = await loaded();
      addMemberToGroup.and.resolveTo({ ok: false, errorKey: 'alerts.group.add_member.error_message_service.message_already_member' });
      component.openAdd();
      component.email.setValue('ben@example.com');

      await component.submitAdd();
      fixture.detectChanges();

      expect(component.view()).toBe('add');
      expect(component.email.value).toBe('ben@example.com');
      expect(el.querySelector('.error-email')!.textContent!.trim())
        .toBe('alerts.group.add_member.error_message_service.message_already_member');

      component.onEmailInput();
      fixture.detectChanges();
      expect(el.querySelector('.error-email')).toBeNull();
    });

    it('sperrt während des Requests einen zweiten Versuch', async () => {
      let resolve!: (r: { ok: true }) => void;
      addMemberToGroup.and.returnValue(new Promise(r => resolve = r));
      const { component } = await loaded();
      component.openAdd();
      component.email.setValue('dora@example.com');

      const first = component.submitAdd();
      expect(component.saving()).toBeTrue();
      await component.submitAdd();
      resolve({ ok: true });
      await first;

      expect(addMemberToGroup).toHaveBeenCalledTimes(1);
    });
  });

  describe('Gruppe verschwindet (Codex F-002)', () => {
    for (const view of ['list', 'add'] as MembersSheetView[]) {
      it(`schließt in der Ansicht „${view}“ mit GROUP_GONE_ROLE`, async () => {
        const { fixture, component } = await loaded();
        component.view.set(view);

        overview.set([aGroupOverview({ id: URLAUB })]);
        fixture.detectChanges();

        expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_GONE_ROLE, MODAL_ID);
      });
    }

    it('schließt auch während eines Requests; das spätere Ergebnis ändert die Ansicht nicht mehr', async () => {
      let resolve!: (r: { ok: true }) => void;
      addMemberToGroup.and.returnValue(new Promise(r => resolve = r));
      const { fixture, component } = await loaded();
      component.openAdd();
      component.email.setValue('dora@example.com');
      const submit = component.submitAdd();

      overview.set([]);
      fixture.detectChanges();
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, GROUP_GONE_ROLE, MODAL_ID);

      resolve({ ok: true });
      await submit;
      expect(component.view()).toBe('add');
      expect(modalCtrl.dismiss).toHaveBeenCalledTimes(1);
    });
  });

  // Schmales Display, lange Namen: kein horizontaler Überlauf, Badge und ⋮ behalten ihre Größe
  describe('Layout', () => {
    const WIDTH = 320;

    it('kein Überlauf; langer Name per Ellipsis, voller Text im DOM; ⋮ mindestens 44 px; „Mitglied hinzufügen“ über der Liste', async () => {
      // Ohne Loader zeigt die Pipe den Schlüssel; für die Breite zählen die echten (kurzen) Texte
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('de', { groups: { members_sheet: { admin_badge: 'Admin', section_members: 'MITGLIEDER', btn_add_member: 'Mitglied hinzufügen' } } }, true);
      translate.use('de');
      const longName = 'Maximiliane-Alexandra von Hohenzollern-Sigmaringen';
      const { fixture, el } = await loaded(wgMembers({ ownerName: longName, members: [aUser({ userName: `${longName} II` })] }));
      Object.assign(el.style, { position: 'fixed', bottom: '0', left: '0', width: `${WIDTH}px`, display: 'block' });
      fixture.detectChanges();
      await ionicReady(el);
      await new Promise(resolve => requestAnimationFrame(resolve));

      const ownerName = el.querySelector('.member-name') as HTMLElement;
      const button = el.querySelector('.member-menu-button') as HTMLElement;
      expect(el.scrollWidth).toBeLessThanOrEqual(WIDTH);
      expect(ownerName.textContent!.trim()).toBe(longName);
      expect(ownerName.scrollWidth).toBeGreaterThan(ownerName.clientWidth);
      expect(el.querySelector('.admin-badge')!.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
      expect(button.getBoundingClientRect().right).toBeLessThanOrEqual(WIDTH + 0.5);
      expect(button.getBoundingClientRect().width).toBeGreaterThanOrEqual(44);
      const add = (el.querySelector('.btn-add-member') as HTMLElement).getBoundingClientRect();
      expect(add.right).toBeLessThanOrEqual(WIDTH + 0.5);
      expect(add.bottom).toBeLessThanOrEqual(el.querySelector('.member-list')!.getBoundingClientRect().top + 0.5);
    });
  });
});
