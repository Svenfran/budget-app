import { HttpTestingController } from '@angular/common/http/testing';
import { effect } from '@angular/core';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
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

  describe('stilles Laden (Resync, DIV-36)', () => {
    const serverError = { status: 500, statusText: 'Server Error' };
    let stored: jasmine.Spy;

    /** Aktive Gruppe direkt setzen (ohne Storage), danach Storage-Schreibzugriffe beobachten. */
    function givenActive(group: Group) {
      service.activeGroup.set(group);
      stored = spyOn(storage, 'setItem').and.callThrough();
    }

    function resyncSideNav(groups: Group[], isCurrent?: () => boolean) {
      service.getGroupsForSideNav({ silent: true, isCurrent });
      http.expectOne(`${api}/sidenav`).flush(groups);
      flushMicrotasks();
    }

    it('lässt die aktive Gruppe unverändert (dieselbe Referenz, kein Storage-Write), wenn sie unverändert existiert', fakeAsync(() => {
      const wg = aGroup({ id: 1, name: 'WG' });
      givenActive(wg);
      const loaded = [aGroup({ id: 1, name: 'WG' }), aGroup({ id: 2 })];

      resyncSideNav(loaded);

      expect(service.groupsSideNav()).toEqual(loaded);
      expect(service.activeGroup()).toBe(wg);
      expect(stored).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('übernimmt einen geänderten Namen der aktiven Gruppe, ohne die Gruppen-ID zu ändern', fakeAsync(() => {
      givenActive(aGroup({ id: 1, name: 'WG' }));

      resyncSideNav([aGroup({ id: 1, name: 'Neue WG' })]);

      expect(service.activeGroup().name).toBe('Neue WG');
      expect(service.activeGroupId()).toBe(1);
    }));

    it('macht die erste Gruppe aktiv, wenn die aktive Gruppe nicht mehr existiert', fakeAsync(() => {
      givenActive(aGroup({ id: 99 }));

      resyncSideNav([aGroup({ id: 1 }), aGroup({ id: 2 })]);

      expect(service.activeGroupId()).toBe(1);
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('setzt ohne Gruppen die Platzhalter-Gruppe und leitet zu no-group', fakeAsync(() => {
      givenActive(aGroup({ id: 1 }));

      resyncSideNav([]);

      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
      expect(service.hasNoGroups()).toBeTrue();
      expect(navigate).toHaveBeenCalledWith('/no-group', { replaceUrl: true });
    }));

    it('verlässt no-group, wenn nach einer Zeit ohne Gruppen wieder eine Gruppe existiert (z. B. Einladung offline erhalten)', fakeAsync(() => {
      givenActive(Init.DEFAULT_GROUP);
      service.hasNoGroups.set(true);
      const wg = aGroup({ id: 5, name: 'WG' });

      resyncSideNav([wg]);

      expect(service.activeGroup()).toEqual(jasmine.objectContaining({ id: 5, name: 'WG' }));
      expect(service.hasNoGroups()).toBeFalse();
      expect(navigate).toHaveBeenCalledWith('/domains/tabs/overview', { replaceUrl: true });
    }));

    it('lässt Seitenleiste und aktive Gruppe bei einem Fehler unverändert', fakeAsync(() => {
      spyOn(console, 'warn');
      const { sideNav, wg } = givenGroups();
      givenActive(wg);

      service.getGroupsForSideNav({ silent: true });
      http.expectOne(`${api}/sidenav`).flush('Server error', serverError);
      flushMicrotasks();

      expect(service.groupsSideNav()).toBe(sideNav);
      expect(service.activeGroup()).toBe(wg);
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('verwirft eine überholte Seitenleisten-Antwort (isCurrent beim Eintreffen false)', fakeAsync(() => {
      const { sideNav, wg } = givenGroups();
      givenActive(wg);

      resyncSideNav([aGroup({ id: 9 })], () => false);

      expect(service.groupsSideNav()).toBe(sideNav);
      expect(service.activeGroup()).toBe(wg);
    }));

    it('Übersicht: ersetzt bei Erfolg, bleibt bei Fehler oder überholter Antwort unverändert', () => {
      spyOn(console, 'warn');
      const { overview } = givenGroups();
      const loaded = [anOverview(aGroup({ id: 7 }))];

      service.getGroupsForOverview({ silent: true });
      http.expectOne(`${api}/overview`).flush('Server error', serverError);
      expect(service.groupOverviewList()).toBe(overview);

      service.getGroupsForOverview({ silent: true, isCurrent: () => false });
      http.expectOne(`${api}/overview`).flush(loaded);
      expect(service.groupOverviewList()).toBe(overview);

      service.getGroupsForOverview({ silent: true, isCurrent: () => true });
      http.expectOne(`${api}/overview`).flush(loaded);
      expect(service.groupOverviewList()).toEqual(loaded);
    });

    it('Mitglieder: ersetzt bei Erfolg, bleibt bei Fehler unverändert (kein Platzhalter)', () => {
      spyOn(console, 'warn');
      const current = someMembers({ id: 1 });
      service.groupMembers.set(current);

      service.getGroupMembers(1, { silent: true });
      http.expectOne(`${api}/members/1`).flush(notOwnerOfGroup, forbidden);
      expect(service.groupMembers()).toBe(current);

      const loaded = someMembers({ id: 1, ownerName: 'Ben' });
      service.getGroupMembers(1, { silent: true });
      http.expectOne(`${api}/members/1`).flush(loaded);
      expect(service.groupMembers()).toEqual(loaded);
    });

    it('Mitglieder: verwirft die Antwort für Gruppe 1, wenn inzwischen die Mitglieder von Gruppe 2 geöffnet sind', () => {
      service.groupMembers.set(someMembers({ id: 1 }));

      service.getGroupMembers(1, { silent: true });
      const opened = someMembers({ id: 2 });
      service.groupMembers.set(opened);
      http.expectOne(`${api}/members/1`).flush(someMembers({ id: 1, ownerName: 'Ben' }));

      expect(service.groupMembers()).toBe(opened);
    });

    it('Mitglieder: verwirft eine überholte Antwort (isCurrent false, z. B. Nutzerwechsel)', () => {
      const current = someMembers({ id: 1 });
      service.groupMembers.set(current);

      service.getGroupMembers(1, { silent: true, isCurrent: () => false });
      http.expectOne(`${api}/members/1`).flush(someMembers({ id: 1, ownerName: 'Ben' }));

      expect(service.groupMembers()).toBe(current);
    });

    it('activeGroupId löst einen Effect nur bei Gruppenwechsel aus, nicht bei Umbenennung', () => {
      const runs: number[] = [];
      TestBed.runInInjectionContext(() => effect(() => { runs.push(service.activeGroupId()); }));
      service.setActiveGroup(aGroup({ id: 1, name: 'WG' }));
      TestBed.flushEffects();

      service.setActiveGroup(aGroup({ id: 1, name: 'Neue WG' }));
      TestBed.flushEffects();
      service.setActiveGroup(aGroup({ id: 2, name: 'Urlaub' }));
      TestBed.flushEffects();

      expect(runs).toEqual([1, 2]);
    });
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

    it('ein Fehler verwirft keine zwischenzeitliche Änderung (z. B. Live-Event), Regression DIV-1', () => {
      const { wg, urlaub } = givenGroups();

      service.updateGroup({ ...wg, name: 'Wohngemeinschaft' });
      // während der Request läuft, benennt ein anderes Mitglied „Urlaub“ um (update-group-Event)
      service.groupsSideNav.update(groups => groups.map(g => g.id === urlaub.id ? { ...g, name: 'Sommerurlaub' } : g));
      service.groupOverviewList.update(groups => groups.map(g => g.id === urlaub.id ? { ...g, name: 'Sommerurlaub' } : g));
      http.expectOne(`${api}/update`).flush(notOwnerOfGroup, forbidden);

      expect(service.groupsSideNav()[1].name).toBe('Sommerurlaub');
      expect(service.groupOverviewList()[1].name).toBe('Sommerurlaub');
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

describe('GroupService – Anmelde-Sitzung (DIV-36)', () => {
  let service: GroupService;
  let session: BehaviorSubject<User | null>;

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alertServiceSpy() },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(GroupService);
  });

  it('zählt Login, Logout und Nutzerwechsel als neue Sitzung, nicht die erneute Emission desselben Nutzers', () => {
    const start = service.session();

    session.next(aLoggedInUser({ id: 7 }));
    expect(service.session()).withContext('Login').toBe(start + 1);
    session.next(aLoggedInUser({ id: 7 }));
    expect(service.session()).withContext('derselbe Nutzer erneut (z. B. setUserData)').toBe(start + 1);
    session.next(null);
    expect(service.session()).withContext('Logout').toBe(start + 2);
    session.next(aLoggedInUser({ id: 7 }));
    expect(service.session()).withContext('derselbe Nutzer wieder angemeldet').toBe(start + 3);
    session.next(aLoggedInUser({ id: 8 }));
    expect(service.session()).withContext('Nutzerwechsel').toBe(start + 4);
  });

  it('sessionGuard (Resync-isCurrent) bleibt bei erneuter Emission desselben Nutzers gültig', () => {
    session.next(aLoggedInUser({ id: 7 }));
    const isCurrent = service.sessionGuard();

    session.next(aLoggedInUser({ id: 7 }));

    expect(isCurrent()).toBeTrue();
  });

  it('sessionGuard verwirft nach Logout und bleibt ungültig, auch wenn sich derselbe Nutzer wieder anmeldet', () => {
    session.next(aLoggedInUser({ id: 7 }));
    const isCurrent = service.sessionGuard();

    session.next(null);
    expect(isCurrent()).withContext('nach Logout').toBeFalse();
    session.next(aLoggedInUser({ id: 7 }));

    expect(isCurrent()).withContext('nach erneutem Login desselben Nutzers').toBeFalse();
  });

  it('setzt beim Logout die aktive Gruppe auf den Platzhalter', () => {
    session.next(aLoggedInUser({ id: 7 }));
    service.activeGroup.set(aGroup({ id: 5 }));

    session.next(null);

    expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
  });
});

describe('GroupService – Antwort nach Sitzungswechsel (DIV-58)', () => {
  let service: GroupService;
  let http: HttpTestingController;
  let storage: StorageService;
  let navigate: jasmine.Spy;
  let session: BehaviorSubject<User | null>;
  const sideNavUrl = `${environment.apiBaseUrl}/api/groups/sidenav`;
  const serverError = { status: 500, statusText: 'Server Error' };
  const ids = (groups: { id: number }[]) => groups.map(g => g.id);

  const login = (id: number) => session.next(aLoggedInUser({ id }));
  const logout = () => session.next(null);

  function storedActiveGroup(): Group | null {
    let stored: Group | null = null;
    storage.getItem<Group>('ACTIVE_GROUP').then(group => stored = group);
    flushMicrotasks();
    return stored;
  }

  /**
   * Die ersten `count` Storage-Lesezugriffe bleiben offen, bis der Test sie auflöst; danach liest der Fake normal.
   * Das Array füllt sich erst mit den Zugriffen – per Index aufrufen, nicht vorab destrukturieren.
   */
  function holdStorageReads(count: number): ((group: Group | null) => void)[] {
    const read = storage.getItem.bind(storage);
    const pending: ((group: Group | null) => void)[] = [];
    spyOn(storage, 'getItem').and.callFake(<T>(key: string): Promise<T | null> => pending.length < count
      ? new Promise<T | null>(resolve => pending.push(resolve as (group: Group | null) => void))
      : read<T>(key));
    return pending;
  }

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    storage = storageServiceFake();
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alertServiceSpy() },
        { provide: StorageService, useValue: storage },
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(GroupService);
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => http.verify());

  describe('getGroupsForSideNav', () => {
    it('verwirft eine Antwort, die nach dem Logout eintrifft (Zustand, Storage, Navigation unverändert)', fakeAsync(() => {
      login(7);
      const stored = spyOn(storage, 'setItem').and.callThrough();
      service.getGroupsForSideNav();
      const request = http.expectOne(sideNavUrl);

      logout();
      request.flush([aGroup({ id: 1 }), aGroup({ id: 2 })]);
      flushMicrotasks();

      expect(service.groupsSideNav()).toEqual([]);
      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
      expect(service.hasNoGroups()).toBeFalse();
      expect(stored).not.toHaveBeenCalled();
      expect(storedActiveGroup()).toBeNull();
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('leitet bei einer leeren Antwort nach dem Logout nicht zu no-group', fakeAsync(() => {
      login(7);
      service.getGroupsForSideNav();
      const request = http.expectOne(sideNavUrl);

      logout();
      request.flush([]);
      flushMicrotasks();

      expect(service.hasNoGroups()).toBeFalse();
      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('lässt Seitenleiste und aktive Gruppe des neuen Nutzers stehen, wenn die alte Antwort danach eintrifft', fakeAsync(() => {
      const groupsOfOld = [aGroup({ id: 1 }), aGroup({ id: 2 })];
      const groupsOfNew = [aGroup({ id: 3 }), aGroup({ id: 4 })];
      login(7);
      service.getGroupsForSideNav();
      const oldRequest = http.expectOne(sideNavUrl);
      logout();
      login(8);
      service.getGroupsForSideNav();
      const newRequest = http.expectOne(sideNavUrl);

      newRequest.flush(groupsOfNew);
      flushMicrotasks();
      oldRequest.flush(groupsOfOld);
      flushMicrotasks();

      expect(ids(service.groupsSideNav())).toEqual([3, 4]);
      expect(service.activeGroup().id).toBe(3);
      expect(storedActiveGroup()?.id).toBe(3);
    }));

    it('ändert aktive Gruppe, Storage und Navigation nicht, wenn der Logout während des Storage-Lesens erfolgt', fakeAsync(() => {
      login(7);
      const storageReads = holdStorageReads(1);
      const stored = spyOn(storage, 'setItem').and.callThrough();
      service.getGroupsForSideNav();
      http.expectOne(sideNavUrl).flush([]);
      flushMicrotasks();

      logout();
      storageReads[0](null);
      flushMicrotasks();

      expect(service.activeGroup()).toEqual(Init.DEFAULT_GROUP);
      expect(service.hasNoGroups()).toBeFalse();
      expect(stored).not.toHaveBeenCalled();
      expect(storedActiveGroup()).toBeNull();
      expect(navigate).not.toHaveBeenCalled();
    }));

    it('leert die Seitenleiste der neuen Sitzung nicht, wenn eine alte Anfrage danach fehlschlägt', fakeAsync(() => {
      login(7);
      service.getGroupsForSideNav();
      const oldRequest = http.expectOne(sideNavUrl);
      logout();
      login(8);
      const groupsOfNew = [aGroup({ id: 3 })];
      service.groupsSideNav.set(groupsOfNew);

      oldRequest.flush('Server error', serverError);
      flushMicrotasks();

      expect(service.groupsSideNav()).toBe(groupsOfNew);
    }));

    it('übernimmt die Antwort bei erneuter Emission desselben Nutzers (gleiche Sitzung)', fakeAsync(() => {
      login(7);
      service.getGroupsForSideNav();
      const request = http.expectOne(sideNavUrl);

      login(7);
      request.flush([aGroup({ id: 1 }), aGroup({ id: 2 })]);
      flushMicrotasks();

      expect(ids(service.groupsSideNav())).toEqual([1, 2]);
      expect(service.activeGroup().id).toBe(1);
      expect(storedActiveGroup()?.id).toBe(1);
    }));

    it('innerhalb einer Sitzung gilt die zuletzt eingetroffene Antwort, auch wenn ihr Storage-Lesen zuerst fertig ist', fakeAsync(() => {
      login(7);
      const storageReads = holdStorageReads(2);
      service.getGroupsForSideNav();
      service.getGroupsForSideNav();
      const [first, second] = http.match(sideNavUrl);

      first.flush([aGroup({ id: 1 })]);
      second.flush([aGroup({ id: 2 }), aGroup({ id: 3 })]);
      storageReads[1](null);
      flushMicrotasks();
      storageReads[0](null);
      flushMicrotasks();

      expect(ids(service.groupsSideNav())).toEqual([2, 3]);
      expect(service.activeGroup().id).toBe(2);
    }));
  });

  describe('getGroupsForOverview', () => {
    const overviewUrl = `${environment.apiBaseUrl}/api/groups/overview`;
    const anOverview = (id: number): GroupOverview =>
      ({ id, name: `Gruppe ${id}`, dateCreated: REFERENCE_DATE, ownerName: 'Anna', memberCount: 1 });

    it('verwirft eine Antwort, die nach dem Logout eintrifft', () => {
      login(7);
      service.getGroupsForOverview();
      const request = http.expectOne(overviewUrl);

      logout();
      request.flush([anOverview(1)]);

      expect(service.groupOverviewList()).toEqual([]);
    });

    it('lässt die Übersicht des neuen Nutzers stehen, wenn die alte Antwort danach eintrifft', () => {
      login(7);
      service.getGroupsForOverview();
      const oldRequest = http.expectOne(overviewUrl);
      logout();
      login(8);
      service.getGroupsForOverview();
      const newRequest = http.expectOne(overviewUrl);

      newRequest.flush([anOverview(3)]);
      oldRequest.flush([anOverview(1), anOverview(2)]);

      expect(ids(service.groupOverviewList())).toEqual([3]);
    });

    it('leert die Übersicht der neuen Sitzung nicht, wenn eine alte Anfrage danach fehlschlägt', () => {
      login(7);
      service.getGroupsForOverview();
      const oldRequest = http.expectOne(overviewUrl);
      logout();
      login(8);
      const overviewOfNew = [anOverview(3)];
      service.groupOverviewList.set(overviewOfNew);

      oldRequest.flush('Server error', serverError);

      expect(service.groupOverviewList()).toBe(overviewOfNew);
    });

    it('übernimmt die Antwort bei erneuter Emission desselben Nutzers (gleiche Sitzung)', () => {
      login(7);
      service.getGroupsForOverview();
      const request = http.expectOne(overviewUrl);

      login(7);
      request.flush([anOverview(1), anOverview(2)]);

      expect(ids(service.groupOverviewList())).toEqual([1, 2]);
    });
  });
});
