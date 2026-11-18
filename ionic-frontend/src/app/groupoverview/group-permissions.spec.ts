import { aGroupOverview, aLoggedInUser } from 'src/testing/test-data';
import { isGroupAdmin } from './group-permissions';

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
});
