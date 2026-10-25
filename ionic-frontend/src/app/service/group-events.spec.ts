import { aGroup, aUser } from 'src/testing/test-data';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { applyOwnerChange, renameGroup } from './group-events';

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

    it('lässt die Mitgliederliste einer anderen Gruppe unverändert', () => {
      const members = givenMembers();

      const result = applyOwnerChange(members, { groupId: 99, newOwner: { id: NEW_OWNER_ID, userName: 'Ben' } });

      expect(result).toBe(members);
    });
  });
});
