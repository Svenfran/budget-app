import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { AlertController, IonicModule, IonInput, ModalController } from '@ionic/angular';
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
import { OverviewService } from 'src/app/domains/overview/service/overview.service';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';

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
  let loadUserBalances: jasmine.Spy;

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
    // Ohne offenen Saldo bleibt der bisherige Bestätigungs-Alert (DIV-109)
    loadUserBalances = jasmine.createSpy('loadUserBalances').and.resolveTo([]);
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
        { provide: OverviewService, useValue: { loadUserBalances } },
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

  describe('Warnung bei offenem Saldo (DIV-109, AK5)', () => {
    const saldo = (userId: number, diff: number) => ({ userId, userName: 'x', sum: 0, diff });
    const betrag = (diff: number) =>
      new ScientificCurrencyPipe().transform(diff, overview().find(group => group.id === WG)!.currencyCode);

    it('Entfernen eines Mitglieds mit offenem Saldo: Alert nennt den Saldo, Entfernen bleibt möglich', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      loadUserBalances.and.resolveTo([saldo(anna.id, 100), saldo(cleo.id, -100)]);
      const { component } = await loaded();

      await component.removeMember(cleo);

      expect(loadUserBalances).toHaveBeenCalledOnceWith(WG);
      expect(translate).toHaveBeenCalledWith('alerts.group.remove_member_open_balance.message',
        { memberName: 'Cleo', group: 'WG', balance: betrag(-100) });
      expect(removeMemberFromGroup).toHaveBeenCalledOnceWith({ id: WG, name: 'WG', member: cleo });
    });

    it('eigener Austritt mit offenem Saldo: eigener Text, Austritt bleibt möglich', async () => {
      const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
      session.next(aLoggedInUser({ id: ben.id, name: 'Ben' }));
      loadUserBalances.and.resolveTo([saldo(ben.id, 25.5)]);
      const { component } = await loaded();

      await component.removeMember(ben);

      expect(translate).toHaveBeenCalledWith('alerts.group.leave_open_balance.message',
        { memberName: 'Ben', group: 'WG', balance: betrag(25.5) });
      expect(modalCtrl.dismiss).toHaveBeenCalledOnceWith(undefined, MEMBERS_SHEET_LEFT_ROLE, MODAL_ID);
    });

    [
      { fall: 'Saldo 0', balances: [saldo(cleo.id, 0)] },
      { fall: 'Saldo rundet auf 0 Cent', balances: [saldo(cleo.id, 0.004)] },
      { fall: 'Mitglied fehlt in den Salden', balances: [saldo(ben.id, 50)] },
      { fall: 'Salden nicht ladbar', balances: null },
    ].forEach(({ fall, balances }) => {
      it(`${fall}: bisheriger Alert ohne Saldo`, async () => {
        const translate = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();
        loadUserBalances.and.resolveTo(balances);
        const { component } = await loaded();

        await component.removeMember(cleo);

        expect(translate).toHaveBeenCalledWith('alerts.group.remove_member.message', { memberName: 'Cleo', group: 'WG' });
        expect(translate).not.toHaveBeenCalledWith('alerts.group.remove_member_open_balance.message', jasmine.anything());
      });
    });

    it('sperrt während der Saldo-Abfrage: zweites Auslösen ohne zweiten Alert', async () => {
      let resolve!: (balances: unknown) => void;
      loadUserBalances.and.returnValue(new Promise(r => resolve = r));
      const { component } = await loaded();

      const first = component.removeMember(cleo);
      expect(component.saving()).toBeTrue();
      await component.removeMember(cleo);
      resolve([saldo(cleo.id, -10)]);
      await first;

      expect(loadUserBalances).toHaveBeenCalledTimes(1);
      expect(alertCreate).toHaveBeenCalledTimes(1);
      expect(component.saving()).toBeFalse();
    });

    it('Sheet schließt während der Saldo-Abfrage: kein Alert, nichts entfernt', async () => {
      let resolve!: (balances: unknown) => void;
      loadUserBalances.and.returnValue(new Promise(r => resolve = r));
      const { fixture, component } = await loaded();

      const pending = component.removeMember(cleo);
      overview.set([]);
      fixture.detectChanges();
      resolve([saldo(cleo.id, -10)]);
      await pending;

      expect(alertCreate).not.toHaveBeenCalled();
      expect(removeMemberFromGroup).not.toHaveBeenCalled();
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

    it('fokussiert beim Wechsel in die Eingabe wirklich das E-Mail-Feld – auch beim zweiten Mal', async () => {
      const { fixture, component, el } = await loaded();
      const nativeInput = async () => {
        fixture.detectChanges();
        const input = el.querySelector('ion-input.member-email-input') as HTMLIonInputElement;
        await ionicReady(el);
        // das Sheet fokussiert einen Frame nach dem Rendern; zwei Frames warten
        for (let i = 0; i < 2; i++) await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve)));
        return input.getInputElement();
      };

      component.openAdd();
      const input = await nativeInput();
      expect(document.activeElement).toBe(input);

      component.cancelAdd();
      fixture.detectChanges();
      (document.activeElement as HTMLElement | null)?.blur();
      component.openAdd();
      const second = await nativeInput();
      expect(document.activeElement).toBe(second);
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

    // Codex-Implementierungsreview F-002: ein offener Bestätigungs-Alert gehört zum Sheet und schließt mit ihm
    it('offener Bestätigungs-Alert schließt mit dem Sheet; die Aktion wird nicht ausgeführt', async () => {
      let dismissed!: (value: { role: string }) => void;
      const dismiss = jasmine.createSpy('dismiss').and.callFake(async () => { dismissed({ role: 'backdrop' }); return true; });
      const present = jasmine.createSpy('present').and.resolveTo();
      alertCreate.and.callFake(async () => ({
        present, dismiss, onDidDismiss: () => new Promise(resolve => dismissed = resolve),
      }));
      const { fixture, component } = await loaded();

      const pending = component.removeMember(cleo);
      await settle();
      expect(present).toHaveBeenCalled();

      overview.set([]);
      fixture.detectChanges();
      await pending;

      expect(dismiss).toHaveBeenCalled();
      expect(removeMemberFromGroup).not.toHaveBeenCalled();
    });

    it('Alert, der erst nach dem Schließen fertig erzeugt ist, wird nicht angezeigt und wieder entfernt (Codex-Review 2 F-003)', async () => {
      let created!: (alert: unknown) => void;
      const present = jasmine.createSpy('present').and.resolveTo();
      // Ionic hängt den Alert schon beim Erzeugen an; ohne present() hilft nur remove()
      const remove = jasmine.createSpy('remove');
      alertCreate.and.returnValue(new Promise(resolve => created = resolve));
      const { fixture, component } = await loaded();

      const pending = component.makeAdmin(ben);
      fixture.destroy();
      created({ present, remove, dismiss: jasmine.createSpy('dismiss').and.resolveTo(true), onDidDismiss: () => Promise.resolve({ role: 'confirm' }) });
      await pending;

      expect(present).not.toHaveBeenCalled();
      expect(remove).toHaveBeenCalledTimes(1);
      expect(changeGroupOwner).not.toHaveBeenCalled();
    });

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

    // Abbrechen bei offener Tastatur: Die Liste erscheint sofort, 'keyboardWillHide' entfernt den Offset erst danach.
    // Mit Offset wäre die Liste kurz bis zu 90vh hoch (Flackern über die ganze Seite) – nur die Eingabe nutzt ihn.
    describe('Tastatur-Offset', () => {
      afterEach(() => document.documentElement.style.removeProperty('--keyboard-offset'));

      it('gilt nur in der Eingabe, nicht in der Liste', async () => {
        document.documentElement.style.setProperty('--keyboard-offset', '300px');
        const { fixture, component, el } = await loaded();
        const sheet = () => el.querySelector('.members-sheet') as HTMLElement;
        const vh90 = window.innerHeight * 0.9;

        component.openAdd();
        fixture.detectChanges();
        expect(getComputedStyle(sheet()).marginBottom).toBe('300px');
        expect(parseFloat(getComputedStyle(sheet()).maxHeight)).toBeCloseTo(vh90 - 300, 0);

        component.cancelAdd();
        fixture.detectChanges();
        expect(getComputedStyle(sheet()).marginBottom).toBe('0px');
        expect(parseFloat(getComputedStyle(sheet()).maxHeight)).toBeCloseTo(vh90, 0);
      });
    });

    // Codex F-004: Android-Schriftgröße als Root-Schrift (am Dokument), Wirkung per getComputedStyle nachgewiesen
    describe('200 % Schrift', () => {
      afterEach(() => document.documentElement.style.fontSize = '');

      // Ohne Loader zeigt die Pipe lange, unumbrechbare Schlüssel; für die Breite zählen die echten Texte
      beforeEach(() => {
        const translate = TestBed.inject(TranslateService);
        translate.setTranslation('de', { groups: { members_sheet: {
          title: 'Mitglieder verwalten', add_title: 'Mitglied hinzufügen', btn_add_member: 'Mitglied hinzufügen',
          section_members: 'MITGLIEDER', admin_badge: 'Admin', admin_hint: 'Der Gruppenadmin kann Mitglieder entfernen.',
          email_label: 'E-Mail-Adresse', btn_cancel: 'Abbrechen', btn_add: 'Hinzufügen',
        } } }, true);
        translate.use('de');
      });

      async function at200(el: HTMLElement, fixture: ComponentFixture<MembersSheetComponent>) {
        fixture.detectChanges();
        const title = el.querySelector('h2') as HTMLElement;
        const before = parseFloat(getComputedStyle(title).fontSize);
        Object.assign(el.style, { position: 'fixed', bottom: '0', left: '0', width: `${WIDTH}px`, display: 'block' });
        document.documentElement.style.fontSize = '200%';
        fixture.detectChanges();
        await ionicReady(el);
        await new Promise(resolve => requestAnimationFrame(resolve));
        await new Promise(resolve => requestAnimationFrame(resolve));
        expect(parseFloat(getComputedStyle(title).fontSize)).toBeGreaterThanOrEqual(before * 1.9);
      }

      it('Liste: kein Überlauf, Badge, ⋮ und „Mitglied hinzufügen“ innerhalb der Breite, Name gekürzt statt verdrängt', async () => {
        const { fixture, el } = await loaded(wgMembers({ ownerName: 'Maximiliane Beispielhausen' }));
        await at200(el, fixture);

        expect(el.scrollWidth).toBeLessThanOrEqual(WIDTH);
        for (const sel of ['.admin-badge', '.member-menu-button', '.btn-add-member', '.btn-close']) {
          const r = el.querySelector(sel)!.getBoundingClientRect();
          expect(r.width).withContext(sel).toBeGreaterThan(0);
          expect(r.right).withContext(sel).toBeLessThanOrEqual(WIDTH + 0.5);
        }
        expect(el.querySelector('.member-name')!.getBoundingClientRect().width).toBeGreaterThan(0);
      });

      // Codex-Implementierungsreview F-001: Kopf schrumpft nicht; ein Gruppenname mit 200 Zeichen darf Liste, Feld und
      // Buttons nicht aus dem auf 90vh begrenzten Sheet drängen. Höhe wie ein 640 px hohes Handy über das Karma-iframe.
      for (const view of ['list', 'add'] as MembersSheetView[]) {
        it(`Gruppenname mit 200 Zeichen (Ansicht „${view}“): Untertitel höchstens zwei Zeilen, Inhalt und Aktionen sichtbar`, async () => {
          const frame = window.frameElement as HTMLElement | null;
          const frameHeight = frame?.style.height ?? '';
          try {
            if (frame) frame.style.height = '711px'; // 90vh = 640 px
            const longName = 'Wohngemeinschaft '.repeat(12).slice(0, 200);
            const sheet = create(WG, longName);
            answer(loads.length - 1, wgMembers());
            await settle();
            if (view === 'add') sheet.component.openAdd();
            await at200(sheet.el, sheet.fixture);

            const box = sheet.el.querySelector('.members-sheet')!.getBoundingClientRect();
            const subtitle = sheet.el.querySelector('.sheet-subtitle') as HTMLElement;
            const lineHeight = parseFloat(getComputedStyle(subtitle).fontSize) * 1.5;
            expect(subtitle.getBoundingClientRect().height).toBeLessThanOrEqual(lineHeight * 2 + 1);
            expect(subtitle.textContent!.trim()).toBe(longName.trim());
            const body = sheet.el.querySelector('.sheet-body')!.getBoundingClientRect();
            const visible = view === 'list' ? ['.btn-add-member'] : ['ion-item.member-email-field'];
            for (const sel of visible) {
              const r = sheet.el.querySelector(sel)!.getBoundingClientRect();
              expect(r.height).withContext(sel).toBeGreaterThan(0);
              expect(r.top).withContext(sel).toBeGreaterThanOrEqual(body.top - 0.5);
              expect(r.bottom).withContext(sel).toBeLessThanOrEqual(body.bottom + 0.5);
            }
            if (view === 'add') {
              for (const sel of ['.btn-cancel', '.btn-add']) {
                const r = sheet.el.querySelector(sel)!.getBoundingClientRect();
                expect(r.bottom).withContext(sel).toBeLessThanOrEqual(box.bottom + 0.5);
                expect(r.top).withContext(sel).toBeGreaterThanOrEqual(box.top - 0.5);
              }
            }
          } finally {
            if (frame) frame.style.height = frameHeight;
          }
        });
      }

      it('„Mitglied hinzufügen“: Feld und beide Buttons erreichbar, kein Überlauf', async () => {
        const { fixture, component, el } = await loaded();
        component.openAdd();
        await at200(el, fixture);

        expect(el.scrollWidth).toBeLessThanOrEqual(WIDTH);
        for (const sel of ['.member-email-input', '.btn-cancel', '.btn-add']) {
          const r = el.querySelector(sel)!.getBoundingClientRect();
          expect(r.width).withContext(sel).toBeGreaterThan(0);
          expect(r.right).withContext(sel).toBeLessThanOrEqual(WIDTH + 0.5);
        }
      });
    });
  });
});
