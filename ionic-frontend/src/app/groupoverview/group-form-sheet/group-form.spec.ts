import { REFERENCE_DATE } from 'src/testing/test-data';
import { GROUP_NAME_MAX_LENGTH, groupForm, toGroup } from './group-form';

describe('group-form (DIV-80)', () => {
  describe('groupForm', () => {
    it('ist mit einem Namen gültig und übernimmt den Startwert', () => {
      const form = groupForm('Haushalt');

      expect(form.valid).toBeTrue();
      expect(form.getRawValue()).toEqual({ name: 'Haushalt' });
    });

    it('leer oder nur Leerzeichen ist ungültig (required)', () => {
      for (const name of ['', '   ']) {
        const form = groupForm(name);

        expect(form.controls.name.errors).toEqual({ required: true });
      }
    });

    it('höchstens GROUP_NAME_MAX_LENGTH Zeichen', () => {
      expect(groupForm('a'.repeat(GROUP_NAME_MAX_LENGTH)).valid).toBeTrue();
      expect(groupForm('a'.repeat(GROUP_NAME_MAX_LENGTH + 1)).controls.name.errors?.['maxlength']).toBeTruthy();
    });
  });

  describe('toGroup', () => {
    it('neue Gruppe: getrimmter Name, Platzhalter-ID und aktuelles Datum', () => {
      jasmine.clock().install();
      jasmine.clock().mockDate(REFERENCE_DATE);
      try {
        expect(toGroup({ name: '  Verein  ' })).toEqual({ id: REFERENCE_DATE.getTime(), name: 'Verein', dateCreated: REFERENCE_DATE });
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('bestehende Gruppe: ID und Gründungsdatum bleiben, Name getrimmt', () => {
      const existing = { id: 7, dateCreated: new Date('2024-01-01T00:00:00Z') };

      expect(toGroup({ name: ' WG neu ' }, existing)).toEqual({ id: 7, name: 'WG neu', dateCreated: existing.dateCreated });
    });
  });
});
