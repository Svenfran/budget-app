import { aGroupOverview, aLoggedInUser } from 'src/testing/test-data';
import { isGroupAdmin, memberActions } from './group-permissions';

describe('group-permissions', () => {
  describe('isGroupAdmin', () => {
    it('erkennt den Admin an der User-ID', () => {
      const anna = aLoggedInUser({ name: 'Anna' });

      expect(isGroupAdmin(aGroupOverview({ ownerId: anna.id, ownerName: 'Anna' }), anna)).toBeTrue();
    });

    it('ein gleichnamiger Nutzer mit anderer ID ist kein Admin (Regression: früher Namensvergleich)', () => {
      const anna = aLoggedInUser({ name: 'Anna' });

      expect(isGroupAdmin(aGroupOverview({ ownerId: anna.id + 1000, ownerName: 'Anna' }), anna)).toBeFalse();
    });

    it('ohne Nutzer nie Admin', () => {
      const group = aGroupOverview();

      expect(isGroupAdmin(group, null)).toBeFalse();
      expect(isGroupAdmin(group, undefined)).toBeFalse();
    });
  });

  describe('memberActions', () => {
    const ADMIN = 1;
    const ME = 2;
    const OTHER = 3;
    const group = { ownerId: ADMIN };

    it('Admin: bei anderen Mitgliedern „Zum Admin machen“ und „Entfernen“', () => {
      expect(memberActions(group, ADMIN, OTHER)).toEqual({ makeAdmin: true, remove: true });
    });

    it('Admin-Zeile hat nie Aktionen (auch nicht für den Admin selbst)', () => {
      expect(memberActions(group, ADMIN, ADMIN)).toEqual({ makeAdmin: false, remove: false });
      expect(memberActions(group, ME, ADMIN)).toEqual({ makeAdmin: false, remove: false });
    });

    it('Mitglied: nur sich selbst entfernen, keinen Admin machen', () => {
      expect(memberActions(group, ME, ME)).toEqual({ makeAdmin: false, remove: true });
      expect(memberActions(group, ME, OTHER)).toEqual({ makeAdmin: false, remove: false });
    });

    it('ohne Nutzer keine Aktionen', () => {
      expect(memberActions(group, undefined, OTHER)).toEqual({ makeAdmin: false, remove: false });
      expect(memberActions(group, null, OTHER)).toEqual({ makeAdmin: false, remove: false });
    });
  });
});
