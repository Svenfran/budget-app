import { aGroup, aGroupOverview, aUser, REFERENCE_DATE } from 'src/testing/test-data';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import {
  applyMemberAddedToOverview, applyMemberRemovedFromOverview, applyMembersChange, applyOwnerChange,
  applyOwnerChangeToOverview, applyGroupUpdate, GroupMembersEvent, isOwnMembershipEvent,
} from './group-events';

describe('group-events', () => {
  // DIV-77: update-group trägt Name und Währung (vorher renameGroup, nur Name)
  describe('applyGroupUpdate', () => {
    it('übernimmt Name und Währung nur für die betroffene Gruppe', () => {
      const wg = aGroup({ name: 'WG', currencyCode: 'EUR' });
      const urlaub = aGroup({ name: 'Urlaub', currencyCode: 'EUR' });

      const result = applyGroupUpdate([wg, urlaub], { id: wg.id, name: 'Neue WG', currencyCode: 'USD' });

      expect(result).toEqual([{ ...wg, name: 'Neue WG', currencyCode: 'USD' }, urlaub]);
      expect(result[1]).toBe(urlaub);
    });

    it('behält die Währung (z. B. hasBookings der Übersicht), wenn das Payload keine trägt', () => {
      const wg = aGroupOverview({ name: 'WG', currencyCode: 'CHF', hasBookings: true });

      expect(applyGroupUpdate([wg], { id: wg.id, name: 'Neu' })).toEqual([{ ...wg, name: 'Neu' }]);
    });

    it('ändert bei einer lokal unbekannten Gruppe nichts und wirft nicht (deshalb Refetch nach eigener Aufnahme, D8)', () => {
      const wg = aGroup({ currencyCode: 'EUR' });

      expect(applyGroupUpdate([wg], { id: 999, name: 'Unbekannt', currencyCode: 'USD' })).toEqual([wg]);
      expect(applyGroupUpdate([], { id: 999, name: 'Unbekannt', currencyCode: 'USD' })).toEqual([]);
    });
  });

  // DIV-77 (D8): stiller Refetch nur nach der eigenen Aufnahme
  describe('isOwnMembershipEvent', () => {
    it('erkennt die eigene Aufnahme', () => {
      expect(isOwnMembershipEvent({ updatedMember: aUser({ id: 50 }) }, { id: 50 })).toBeTrue();
    });

    it('nicht bei fremdem Mitglied oder ohne angemeldeten Nutzer', () => {
      expect(isOwnMembershipEvent({ updatedMember: aUser({ id: 60 }) }, { id: 50 })).toBeFalse();
      expect(isOwnMembershipEvent({ updatedMember: aUser({ id: 50 }) }, undefined)).toBeFalse();
    });
  });

  // DIV-80: Antwort/Event von Hinzufügen und Entfernen in der geladenen Mitgliederliste
  describe('applyMembersChange', () => {
    const loaded = (): GroupMembers =>
      ({ id: 10, name: 'WG', ownerId: 20, ownerName: 'Anna', members: [aUser({ id: 30 })] });

    it('übernimmt Owner und Mitglieder aus dem neuen Stand der geladenen Gruppe', () => {
      const current = loaded();
      const newMembers = [aUser({ id: 30 }), aUser({ id: 40 })];

      const result = applyMembersChange(current, { ...current, name: 'Ignoriert', members: newMembers });

      expect(result).toEqual({ ...current, members: newMembers });
    });

    it('ändert eine geladene Liste einer anderen Gruppe nicht (gleiche Referenz)', () => {
      const current = loaded();

      expect(applyMembersChange(current, { ...current, id: 11, members: [] })).toBe(current);
    });

    it('ist bei doppeltem Event idempotent', () => {
      const update = { ...loaded(), members: [aUser({ id: 40 })] };

      const once = applyMembersChange(loaded(), update);

      expect(applyMembersChange(once, update)).toEqual(once);
    });
  });

  // DIV-80: Übersicht bei Add/Remove-Member- und Owner-Events (vorher inline in AppComponent)
  describe('Übersicht bei Mitglieder-Events', () => {
    const ME = 50;
    const event = (overrides: Partial<GroupMembersEvent> = {}): GroupMembersEvent => ({
      id: 10, name: 'WG', dateCreated: REFERENCE_DATE, ownerName: 'Anna', ownerId: 20, currencyCode: 'EUR',
      members: [aUser(), aUser()], updatedMember: aUser({ id: 60 }), ...overrides,
    });
    const wg = () => aGroupOverview({ id: 10, name: 'WG', ownerId: 20, memberCount: 1 });
    const urlaub = () => aGroupOverview({ id: 11, name: 'Urlaub', memberCount: 4 });

    describe('applyMemberAddedToOverview', () => {
      it('nimmt die Gruppe mit ownerId auf, wenn der Nutzer selbst hinzugefügt wurde', () => {
        const other = urlaub();

        const result = applyMemberAddedToOverview([other], event({ updatedMember: aUser({ id: ME }) }), ME);

        expect(result).toEqual([other, {
          id: 10, name: 'WG', dateCreated: REFERENCE_DATE, ownerName: 'Anna', ownerId: 20, memberCount: 2, currencyCode: 'EUR',
        }]);
      });

      // Regression Codex-Review 1 F-003: live in eine USD-Gruppe aufgenommen → Übersicht mit USD, hasBookings unbekannt
      it('übernimmt die Währung der Gruppe, hasBookings bleibt unbekannt', () => {
        const result = applyMemberAddedToOverview([], event({ updatedMember: aUser({ id: ME }), currencyCode: 'USD' }), ME);

        expect(result[0].currencyCode).toBe('USD');
        expect(result[0].hasBookings).toBeUndefined();
        expect('hasBookings' in result[0]).toBeFalse();
      });

      it('nimmt die eigene Gruppe nicht doppelt auf (z. B. Resync vor dem Event)', () => {
        const result = applyMemberAddedToOverview([wg()], event({ updatedMember: aUser({ id: ME }) }), ME);

        expect(result.map(g => g.id)).toEqual([10]);
        expect(result[0].memberCount).toBe(2);
      });

      it('aktualisiert bei fremdem Mitglied nur die Mitgliederzahl der betroffenen Gruppe', () => {
        const other = urlaub();

        const before = wg();

        const result = applyMemberAddedToOverview([before, other], event(), ME);

        expect(result).toEqual([{ ...before, memberCount: 2 }, other]);
        expect(result[1]).toBe(other);
      });
    });

    describe('applyMemberRemovedFromOverview', () => {
      it('entfernt die Gruppe, wenn der Nutzer selbst entfernt wurde', () => {
        const other = urlaub();

        expect(applyMemberRemovedFromOverview([wg(), other], event({ updatedMember: aUser({ id: ME }) }), ME)).toEqual([other]);
      });

      it('aktualisiert bei fremdem Mitglied nur die Mitgliederzahl', () => {
        const result = applyMemberRemovedFromOverview([wg(), urlaub()], event({ members: [] }), ME);

        expect(result.map(g => [g.id, g.memberCount])).toEqual([[10, 0], [11, 4]]);
      });

      it('ändert bei unbekannter Gruppe nichts und wirft nicht', () => {
        expect(applyMemberRemovedFromOverview([urlaub()], event(), ME).map(g => g.memberCount)).toEqual([4]);
        expect(applyMemberRemovedFromOverview([], event(), ME)).toEqual([]);
      });
    });

    describe('applyOwnerChangeToOverview', () => {
      it('übernimmt Name und ID des neuen Admins nur für die betroffene Gruppe', () => {
        const other = urlaub();
        const ben = aUser({ id: 30, userName: 'Ben' });

        const result = applyOwnerChangeToOverview([wg(), other], { groupId: 10, newOwner: ben });

        expect([result[0].ownerId, result[0].ownerName]).toEqual([30, 'Ben']);
        expect(result[1]).toBe(other);
      });
    });
  });

  describe('applyOwnerChange', () => {
    /** Gruppen-ID, bisheriger Owner und neuer Owner bewusst verschieden, damit eine vertauschte ID auffällt. */
    const GROUP_ID = 10;
    const PREVIOUS_OWNER_ID = 20;
    const NEW_OWNER_ID = 30;

    function givenMembers(): GroupMembers {
      return {
        id: GROUP_ID,
        name: 'WG',
        ownerId: PREVIOUS_OWNER_ID,
        ownerName: 'Anna',
        members: [aUser({ id: NEW_OWNER_ID, userName: 'Ben' }), aUser({ id: 40, userName: 'Cleo' })],
      };
    }

    it('übernimmt den bisherigen Owner mit seiner User-ID als Mitglied und entfernt den neuen Owner (Regression P4.6)', () => {
      const result = applyOwnerChange(givenMembers(),
        { groupId: GROUP_ID, newOwner: { id: NEW_OWNER_ID, userName: 'Ben' } });

      expect(result.ownerId).toBe(NEW_OWNER_ID);
      expect(result.ownerName).toBe('Ben');
      expect(result.members.map(member => member.id)).toEqual([40, PREVIOUS_OWNER_ID]);
      expect(result.members).toContain({ id: PREVIOUS_OWNER_ID, userName: 'Anna' });
    });

    it('lässt eine bereits aktuelle Liste unverändert, wenn das Event nach einem Resync eintrifft (Regression Review F-002)', () => {
      const resynced: GroupMembers = {
        ...givenMembers(),
        ownerId: NEW_OWNER_ID,
        ownerName: 'Ben',
        members: [aUser({ id: PREVIOUS_OWNER_ID, userName: 'Anna' }), aUser({ id: 40, userName: 'Cleo' })],
      };

      const result = applyOwnerChange(resynced, { groupId: GROUP_ID, newOwner: { id: NEW_OWNER_ID, userName: 'Ben' } });

      expect(result).toBe(resynced);
    });

    it('ist idempotent: dasselbe Event zweimal angewendet ergibt dieselbe Liste wie einmal', () => {
      const event = { groupId: GROUP_ID, newOwner: { id: NEW_OWNER_ID, userName: 'Ben' } };
      const once = applyOwnerChange(givenMembers(), event);

      const twice = applyOwnerChange(once, event);

      expect(twice.members.map(member => member.id)).toEqual([40, PREVIOUS_OWNER_ID]);
      expect(twice.ownerId).toBe(NEW_OWNER_ID);
    });

    it('lässt die Mitgliederliste einer anderen Gruppe unverändert', () => {
      const members = givenMembers();

      const result = applyOwnerChange(members, { groupId: 99, newOwner: { id: NEW_OWNER_ID, userName: 'Ben' } });

      expect(result).toBe(members);
    });
  });
});
