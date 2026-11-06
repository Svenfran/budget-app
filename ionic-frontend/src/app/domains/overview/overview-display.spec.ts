import { isDeletedMarker, isInactiveMember, userDisplayName, userInitials } from './overview-display';

describe('overview-display', () => {
  const DELETED_LABEL = 'Nutzer gelöscht';

  describe('userDisplayName', () => {
    it('gibt einen normalen Namen unverändert zurück', () => {
      expect(userDisplayName('Sven', DELETED_LABEL)).toBe('Sven');
    });

    it('kürzt lange Namen nicht', () => {
      const longName = 'Maximilian-Alexander von Hohenzollern-Sigmaringen';
      expect(userDisplayName(longName, DELETED_LABEL)).toBe(longName);
    });

    it('ersetzt gelöschte Nutzer durch das übersetzte Label', () => {
      expect(userDisplayName('USER DELETED', DELETED_LABEL)).toBe(DELETED_LABEL);
    });

    it('zeigt bei entfernten Mitgliedern nur das erste Wort', () => {
      expect(userDisplayName('Max REMOVED', DELETED_LABEL)).toBe('Max');
    });

    it('behandelt beide Marker als gelöscht', () => {
      expect(userDisplayName('DELETED REMOVED', DELETED_LABEL)).toBe(DELETED_LABEL);
    });
  });

  describe('userInitials', () => {
    it('nimmt bei einem Wort die ersten zwei Zeichen in Großbuchstaben', () => {
      expect(userInitials('Sven')).toBe('SV');
      expect(userInitials('sabine')).toBe('SA');
    });

    it('nimmt bei mehreren Wörtern die Anfangsbuchstaben der ersten beiden', () => {
      expect(userInitials('Anna Lena Maier')).toBe('AL');
    });

    it('liefert bei einem Ein-Zeichen-Namen nur dieses Zeichen', () => {
      expect(userInitials('X')).toBe('X');
    });

    it('behandelt Umlaute korrekt', () => {
      expect(userInitials('Özlem')).toBe('ÖZ');
    });

    it('ignoriert führende, folgende und doppelte Leerzeichen', () => {
      expect(userInitials('  anna   lena ')).toBe('AL');
    });

    it('liefert bei leerem Namen einen leeren String', () => {
      expect(userInitials('')).toBe('');
      expect(userInitials('   ')).toBe('');
    });
  });

  describe('isInactiveMember', () => {
    it('erkennt normale, entfernte, gelöschte und kombinierte Namen', () => {
      expect(isInactiveMember('Sven')).toBeFalse();
      expect(isInactiveMember('Max REMOVED')).toBeTrue();
      expect(isInactiveMember('USER DELETED')).toBeTrue();
      expect(isInactiveMember('DELETED REMOVED')).toBeTrue();
    });
  });

  describe('isDeletedMarker', () => {
    it('erkennt nur gelöschte Nutzer, nicht entfernte', () => {
      expect(isDeletedMarker('Sven')).toBeFalse();
      expect(isDeletedMarker('Max REMOVED')).toBeFalse();
      expect(isDeletedMarker('USER DELETED')).toBeTrue();
      expect(isDeletedMarker('DELETED REMOVED')).toBeTrue();
    });
  });
});
