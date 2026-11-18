import { aGroup, aUser } from 'src/testing/test-data';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { applyMembersChange, applyOwnerChange, renameGroup } from './group-events';

describe('group-events', () => {
  describe('renameGroup', () => {
    it('übernimmt den neuen Namen nur für die betroffene Gruppe', () => {
      const wg = aGroup({ name: 'WG' });
      const urlaub = aGroup({ name: 'Urlaub' });

      const result = renameGroup([wg, urlaub], { id: wg.id, name: 'Neue WG' });

      expect(result).toEqual([{ ...wg, name: 'Neue WG' }, urlaub]);
    });

    it('ändert bei einer lokal unbekannten Gruppe nichts und wirft nicht', () => {
      const wg = aGroup();

      expect(renameGroup([wg], { id: 999, name: 'Unbekannt' })).toEqual([wg]);
      expect(renameGroup([], { id: 999, name: 'Unbekannt' })).toEqual([]);
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
