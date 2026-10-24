import { HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/auth/auth.service';
import { Init } from 'src/app/constants/default-values';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { GroupOverview } from 'src/app/groupoverview/model/group-overview';
import { Group } from 'src/app/model/group';
import { environment } from 'src/environments/environment';
import { aGroup, aLoggedInUser, aUser, REFERENCE_DATE } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { AlertService } from './alert.service';
import { GroupService } from './group.service';
import { StorageService } from './storage.service';

describe('GroupService', () => {
  let service: GroupService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  let storage: StorageService;
  let navigate: jasmine.Spy;
  const api = `${environment.apiBaseUrl}/api/groups`;
  const notOwnerOfGroup = 'User with Id 5 is not the owner of the Group';
  const forbidden = { status: 403, statusText: 'Forbidden' };
  const ids = (groups: { id: number }[]) => groups.map(g => g.id);

  const anOverview = (group: Group, overrides: Partial<GroupOverview> = {}): GroupOverview =>
    ({ id: group.id, name: group.name, dateCreated: group.dateCreated, ownerName: 'Anna', memberCount: 1, ...overrides });
  const someMembers = (overrides: Partial<GroupMembers> = {}): GroupMembers =>
    ({ id: 1, name: 'WG', ownerName: 'Anna', ownerId: 1, members: [aUser()], ...overrides });

  /** Seitenleiste und Übersicht bewusst unterschiedlich belegt, damit ein vertauschter Snapshot auffällt. */
  function givenGroups() {
    const wg = aGroup({ id: 1, name: 'WG' });
    const urlaub = aGroup({ id: 2, name: 'Urlaub' });
    const sideNav = [wg, urlaub];
    const overview = [anOverview(wg, { memberCount: 3 }), anOverview(urlaub), anOverview(aGroup({ id: 3 }))];
    service.groupsSideNav.set(sideNav);
    service.groupOverviewList.set(overview);
    return { wg, urlaub, sideNav, overview };
  }

  beforeEach(() => {
    alerts = alertServiceSpy();
    storage = storageServiceFake();
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alerts },
        { provide: StorageService, useValue: storage },
        { provide: AuthService, useValue: authServiceStub(aLoggedInUser({ name: 'Anna' })) },
      ],
    });
    service = TestBed.inject(GroupService);
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => http.verify());

  describe('getGroupsForSideNav', () => {
    it('macht ohne gespeicherte aktive Gruppe die erste Gruppe aktiv und speichert sie', fakeAsync(() => {
      const groups = [aGroup({ id: 1 }), aGroup({ id: 2 })];

      service.getGroupsForSideNav();
      http.expectOne(`${api}/sidenav`).flush(groups);
      flushMicrotasks();

      expect(service.groupsSideNav()).toEqual(groups);
      expect(service.activeGroup()).toEqual(jasmine.objectContaining({ id: 1, name: groups[0].name }));
      let stored: Group | null = null;
      storage.getItem<Group>('ACTIVE_GROUP').then(g => stored = g);
      flushMicrotasks();
      expect(stored).toEqual(jasmine.objectContaining({ id: 1, name: groups[0].name }));
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('übernimmt den neuen Namen der gespeicherten aktiven Gruppe', fakeAsync(() => {
      storage.setItem('ACTIVE_GROUP', aGroup({ id: 2, name: 'Alter Name' }));

      service.getGroupsForSideNav();
      http.expectOne(`${api}/sidenav`).flush([aGroup({ id: 1 }), aGroup({ id: 2, name: 'Neuer Name' })]);
      flushMicrotasks();

      expect(service.activeGroup()).toEqual(jasmine.objectContaining({ id: 2, name: 'Neuer Name' }));
    }));

    it('macht die erste Gruppe aktiv, wenn die gespeicherte Gruppe nicht mehr existiert', fakeAsync(() => {
      storage.setItem('ACTIVE_GROUP', aGroup({ id: 99 }));

      service.getGroupsForSideNav();
      http.expectOne(`${api}/sidenav`).flush([aGroup({ id: 1 }), aGroup({ id: 2 })]);
      flushMicrotasks();

      expect(service.activeGroup().id).toBe(1);
    }));

    it('setzt ohne Gruppen die Platzhalter-Gruppe und leitet zu no-group', fakeAsync(() => {
      storage.setItem('ACTIVE_GROUP', aGroup({ id: 99 }));

      service.getGroupsForSideNav();
      http.expectOne(`${api}/sidenav`).flush([]);
      flushMicrotasks();

      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
      expect(service.hasNoGroups()).toBeTrue();
      expect(navigate).toHaveBeenCalledWith('/no-group', { replaceUrl: true });
    }));

    it('leert die Seitenleiste bei einem Ladefehler', fakeAsync(() => {
      service.groupsSideNav.set([aGroup()]);

      service.getGroupsForSideNav();
      http.expectOne(`${api}/sidenav`).flush('Server error', { status: 500, statusText: 'Server Error' });
      flushMicrotasks();

      expect(service.groupsSideNav()).toEqual([]);
      expect(navigate).not.toHaveBeenCalled();
    }));
  });

  describe('Laden', () => {
    it('lädt die Gruppenübersicht', () => {
      const loaded = [anOverview(aGroup())];

      service.getGroupsForOverview();
      http.expectOne(`${api}/overview`).flush(loaded);

      expect(service.groupOverviewList()).toEqual(loaded);
    });

    it('leert die Gruppenübersicht bei einem Ladefehler', () => {
      service.groupOverviewList.set([anOverview(aGroup())]);

      service.getGroupsForOverview();
      http.expectOne(`${api}/overview`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.groupOverviewList()).toEqual([]);
    });

    it('lädt die Mitglieder der Gruppe', () => {
      const members = someMembers();

      service.getGroupMembers(1);
      http.expectOne(`${api}/members/1`).flush(members);

      expect(service.groupMembers()).toEqual(members);
    });

    it('setzt die Platzhalter-Mitglieder bei einem Ladefehler', () => {
      service.groupMembers.set(someMembers());

      service.getGroupMembers(1);
      http.expectOne(`${api}/members/1`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupMembers()).toEqual(Init.DEFAULT_GROUP_MEMBERS);
    });

    it('lädt die Mitgliedschaftszeiträume des Nutzers in der Gruppe', () => {
      const history = [{ startDate: REFERENCE_DATE, endDate: REFERENCE_DATE, groupId: 1, userId: 2 }];

      service.getGroupMembershipHistoryForGroupAndUser(aGroup({ id: 1 }));
      http.expectOne(`${api}/history-by-group-and-user/1`).flush(history);

      expect(service.groupMembershipHistory()).toEqual(history);
    });

    it('leert die Mitgliedschaftszeiträume bei einem Ladefehler', () => {
      service.groupMembershipHistory.set([{ startDate: REFERENCE_DATE, endDate: REFERENCE_DATE, groupId: 1, userId: 2 }]);

      service.getGroupMembershipHistoryForGroupAndUser(aGroup({ id: 1 }));
      http.expectOne(`${api}/history-by-group-and-user/1`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.groupMembershipHistory()).toEqual([]);
    });
  });

  describe('addGroup', () => {
    it('fügt die neue Gruppe nach dem Speichern in beide Listen ein und macht sie aktiv', () => {
      const { sideNav, overview } = givenGroups();
      const newGroup = aGroup({ id: undefined, name: 'Verein' });

      service.addGroup(newGroup);
      const req = http.expectOne(`${api}/add`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newGroup);
      req.flush({ ...newGroup, id: 4 });

      const created = { id: 4, name: 'Verein', dateCreated: newGroup.dateCreated };
      expect(service.groupsSideNav()).toEqual([...sideNav, created]);
      expect(service.groupOverviewList()).toEqual([...overview, { ...created, ownerName: 'Anna', memberCount: 0 }]);
      expect(service.activeGroup()).toEqual(created);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('wechselt nach dem Anlegen der ersten Gruppe von no-group zur Übersicht', () => {
      service.hasNoGroups.set(true);

      service.addGroup(aGroup({ id: undefined }), 'no-group');
      http.expectOne(`${api}/add`).flush(aGroup({ id: 4 }));

      expect(service.hasNoGroups()).toBeFalse();
      expect(navigate).toHaveBeenCalledWith('/domains/tabs/overview', { replaceUrl: true });
    });

    it('lässt beide Listen und die aktive Gruppe bei einem Fehler unverändert', () => {
      const { wg, sideNav, overview } = givenGroups();
      service.activeGroup.set(wg);

      service.addGroup(aGroup({ id: undefined }), 'no-group');
      http.expectOne(`${api}/add`).flush('Server error', { status: 500, statusText: 'Server Error' });

      expect(service.groupsSideNav()).toEqual(sideNav);
      expect(service.groupOverviewList()).toEqual(overview);
      expect(service.activeGroup()).toBe(wg);
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('updateGroup', () => {
    it('benennt die aktive Gruppe nach dem Speichern in beiden Listen um und speichert sie neu', async () => {
      const { wg, urlaub, overview } = givenGroups();
      service.activeGroup.set(wg);
      const renamed = { ...wg, name: 'Wohngemeinschaft' };

      service.updateGroup(renamed);
      const req = http.expectOne(`${api}/update`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(renamed);
      req.flush(renamed);

      expect(service.groupsSideNav()).toEqual([renamed, urlaub]);
      expect(service.groupOverviewList()).toEqual([{ ...overview[0], name: 'Wohngemeinschaft' }, overview[1], overview[2]]);
      expect(service.activeGroup()).toEqual(jasmine.objectContaining({ id: 1, name: 'Wohngemeinschaft' }));
      expect(await storage.getItem('ACTIVE_GROUP')).toEqual(jasmine.objectContaining({ id: 1, name: 'Wohngemeinschaft' }));
    });

    it('lässt die aktive Gruppe unverändert, wenn eine andere Gruppe umbenannt wird', () => {
      const { wg, urlaub } = givenGroups();
      service.activeGroup.set(wg);

      service.updateGroup({ ...urlaub, name: 'Sommerurlaub' });
      http.expectOne(`${api}/update`).flush({ ...urlaub, name: 'Sommerurlaub' });

      expect(service.groupsSideNav()[1].name).toBe('Sommerurlaub');
      expect(service.activeGroup()).toBe(wg);
    });

    it('lässt beide Listen bei einem Fehler unverändert', () => {
      const { wg, sideNav, overview } = givenGroups();
      service.activeGroup.set(wg);

      service.updateGroup({ ...wg, name: 'Wohngemeinschaft' });
      http.expectOne(`${api}/update`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupsSideNav()).toEqual(sideNav);
      expect(service.groupOverviewList()).toEqual(overview);
      expect(service.activeGroup()).toBe(wg);
    });
  });

  describe('deleteGroup', () => {
    it('entfernt die aktive Gruppe nach dem Löschen aus beiden Listen und macht die erste verbleibende aktiv', async () => {
      const { wg, urlaub } = givenGroups();
      service.activeGroup.set(wg);

      service.deleteGroup(wg);
      const req = http.expectOne(`${api}/delete/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expect(ids(service.groupsSideNav())).toEqual([2]);
      expect(ids(service.groupOverviewList())).toEqual([2, 3]);
      expect(service.activeGroup()).toEqual(jasmine.objectContaining({ id: 2, name: urlaub.name }));
      expect(await storage.getItem('ACTIVE_GROUP')).toEqual(jasmine.objectContaining({ id: 2 }));
    });

    it('setzt nach dem Löschen der letzten Gruppe die Platzhalter-Gruppe aktiv', () => {
      const only = aGroup({ id: 1 });
      service.groupsSideNav.set([only]);
      service.groupOverviewList.set([anOverview(only)]);
      service.activeGroup.set(only);

      service.deleteGroup(only);
      http.expectOne(`${api}/delete/1`).flush(null);

      expect(service.groupsSideNav()).toEqual([]);
      expect(service.groupOverviewList()).toEqual([]);
      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
    });

    it('lässt beide Listen und die aktive Gruppe bei einem Fehler unverändert', () => {
      const { wg, sideNav, overview } = givenGroups();
      service.activeGroup.set(wg);

      service.deleteGroup(wg);
      http.expectOne(`${api}/delete/1`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupsSideNav()).toEqual(sideNav);
      expect(service.groupOverviewList()).toEqual(overview);
      expect(service.activeGroup()).toBe(wg);
    });
  });

  describe('addMemberToGroup', () => {
    const newMember = { id: 1, name: 'WG', newMemberEmail: 'Neu@Example.com' };

    it('aktualisiert nach dem Hinzufügen die Mitgliederzahl, meldet Erfolg und die Änderung', () => {
      const { overview } = givenGroups();
      const updatesBefore = service.memberUpdated();

      service.addMemberToGroup(newMember);
      const req = http.expectOne(`${api}/add-new-member`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newMember);
      req.flush(someMembers({ id: 1, members: [aUser(), aUser(), aUser(), aUser()] }));

      expect(service.groupOverviewList()).toEqual([{ ...overview[0], memberCount: 4 }, overview[1], overview[2]]);
      expect(alerts.showToast).toHaveBeenCalledWith('alerts.group.add_member.error_message_service.message_success');
      expect(service.memberUpdated()).toBe(updatesBefore + 1);
    });

    [
      { name: 'unbekannte E-Mail', status: 404, body: 'User with email Neu@Example.com not found', key: 'message_no_exists' },
      { name: 'Eigentümer als Mitglied', status: 403, body: 'New member equals group owner', key: 'message_admin_is_member' },
      { name: 'bereits Mitglied', status: 403, body: 'Member already exists', key: 'message_already_member' },
      { name: 'anderer Fehler', status: 403, body: notOwnerOfGroup, key: 'message_default' },
    ].forEach(c => {
      it(`lässt die Übersicht bei einem Fehler (${c.name}) unverändert und zeigt den passenden Hinweis`, () => {
        const { overview } = givenGroups();
        const updatesBefore = service.memberUpdated();

        service.addMemberToGroup(newMember);
        http.expectOne(`${api}/add-new-member`).flush(c.body, { status: c.status, statusText: 'Error' });

        expect(service.groupOverviewList()).toEqual(overview);
        expect(service.memberUpdated()).toBe(updatesBefore);
        expect(alerts.showToast).toHaveBeenCalledOnceWith(`alerts.group.add_member.error_message_service.${c.key}`);
      });
    });
  });

  describe('removeMemberFromGroup', () => {
    const removeMember = () => ({ id: 1, name: 'WG', member: aUser() });

    it('aktualisiert nach dem Entfernen die Mitgliederzahl und meldet die Änderung', () => {
      const { overview } = givenGroups();
      const updatesBefore = service.memberUpdated();
      const dto = removeMember();

      service.removeMemberFromGroup(dto);
      const req = http.expectOne(`${api}/remove-member-from-group`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(dto);
      req.flush(someMembers({ id: 1, members: [aUser()] }));

      expect(service.groupOverviewList()).toEqual([{ ...overview[0], memberCount: 1 }, overview[1], overview[2]]);
      expect(service.memberUpdated()).toBe(updatesBefore + 1);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('leitet nach dem Entfernen zu no-group, wenn die Übersicht leer ist', () => {
      service.groupOverviewList.set([]);

      service.removeMemberFromGroup(removeMember());
      http.expectOne(`${api}/remove-member-from-group`).flush(someMembers({ id: 1, members: [] }));

      expect(navigate).toHaveBeenCalledWith('/no-group', { replaceUrl: true });
    });

    it('lässt Übersicht, Mitglieder und Zähler bei einem Fehler unverändert', () => {
      const { overview } = givenGroups();
      const members = someMembers();
      service.groupMembers.set(members);
      const updatesBefore = service.memberUpdated();

      service.removeMemberFromGroup(removeMember());
      http.expectOne(`${api}/remove-member-from-group`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupOverviewList()).toEqual(overview);
      expect(service.groupMembers()).toBe(members);
      expect(service.memberUpdated()).toBe(updatesBefore);
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('changeGroupOwner', () => {
    const dto = () => ({ newOwner: aUser(), groupId: 1 });

    it('sendet den neuen Eigentümer', () => {
      const body = dto();

      service.changeGroupOwner(body);
      const req = http.expectOne(`${api}/change-groupowner`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush(null);
    });

    it('ändert bei einem Fehler keinen Zustand und zeigt keinen Hinweis', () => {
      const { sideNav, overview } = givenGroups();
      const members = someMembers();
      service.groupMembers.set(members);

      service.changeGroupOwner(dto());
      http.expectOne(`${api}/change-groupowner`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupsSideNav()).toEqual(sideNav);
      expect(service.groupOverviewList()).toEqual(overview);
      expect(service.groupMembers()).toBe(members);
      expect(alerts.showToast).not.toHaveBeenCalled();
      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });
});
