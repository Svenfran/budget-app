import { REFERENCE_DATE } from 'src/testing/test-data';
import { currencyOptions, GROUP_NAME_MAX_LENGTH, groupForm, needsCurrencyConfirmation, toGroup } from './group-form';

describe('group-form (DIV-80)', () => {
  describe('groupForm', () => {
    it('ist mit einem Namen gültig und übernimmt den Startwert', () => {
      const form = groupForm('Haushalt');

      expect(form.valid).toBeTrue();
      expect(form.getRawValue()).toEqual({ name: 'Haushalt', currencyCode: 'EUR' });
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
        expect(toGroup({ name: '  Verein  ', currencyCode: 'USD' }))
          .toEqual({ id: REFERENCE_DATE.getTime(), name: 'Verein', dateCreated: REFERENCE_DATE, currencyCode: 'USD' });
      } finally {
        jasmine.clock().uninstall();
      }
    });

    it('bestehende Gruppe: ID und Gründungsdatum bleiben, Name getrimmt', () => {
      const existing = { id: 7, dateCreated: new Date('2024-01-01T00:00:00Z') };

      expect(toGroup({ name: ' WG neu ', currencyCode: 'EUR' }, existing))
        .toEqual({ id: 7, name: 'WG neu', dateCreated: existing.dateCreated, currencyCode: 'EUR' });
    });
  });

  // DIV-77: Währung
  describe('Währung', () => {
    it('neue Gruppe: EUR vorausgewählt; Bearbeiten: Währung der Gruppe; fehlend oder kein ISO-Code: EUR', () => {
      expect(groupForm('Neu').controls.currencyCode.value).toBe('EUR');
      expect(groupForm('WG', 'CHF').controls.currencyCode.value).toBe('CHF');
      expect(groupForm('WG', undefined).controls.currencyCode.value).toBe('EUR');
      ['', 'usd', 'EURO', 'E1R'].forEach(code => expect(groupForm('WG', code).controls.currencyCode.value).toBe('EUR'));
    });

    it('dieser App-Version unbekannte Währung (z. B. später eingeführt) bleibt beim Bearbeiten erhalten', () => {
      const form = groupForm('WG', 'JPY');

      expect(form.controls.currencyCode.value).toBe('JPY');
      expect(toGroup(form.getRawValue(), { id: 7, dateCreated: new Date(0) }).currencyCode).toBe('JPY');
      expect(needsCurrencyConfirmation({ currencyCode: 'JPY', hasBookings: true }, form.getRawValue())).toBeFalse();
    });

    it('currencyOptions: alle unterstützten Währungen; eine unbekannte aktuelle am Ende ohne Namen', () => {
      const standard = currencyOptions('EUR');
      expect(standard.map(o => o.code)).toEqual(['EUR', 'USD', 'GBP', 'CHF', 'CAD', 'AUD', 'MXN', 'BRL', 'PLN', 'CZK', 'NOK', 'SEK']);
      expect(standard.every(o => o.known)).toBeTrue();
      expect(currencyOptions()).toEqual(standard);

      const mitUnbekannter = currencyOptions('JPY');
      expect(mitUnbekannter.length).toBe(13);
      expect(mitUnbekannter[12]).toEqual({ code: 'JPY', symbol: '¥', known: false });
    });

    ([
      { fall: 'Währung geändert, Buchungen', hasBookings: true, code: 'USD', erwartet: true },
      { fall: 'Währung geändert, hasBookings unbekannt (Live-Event)', hasBookings: undefined, code: 'USD', erwartet: true },
      { fall: 'Währung geändert, keine Buchungen', hasBookings: false, code: 'USD', erwartet: false },
      { fall: 'Währung unverändert, Buchungen', hasBookings: true, code: 'EUR', erwartet: false },
    ] as const).forEach(({ fall, hasBookings, code, erwartet }) => {
      it(`needsCurrencyConfirmation: ${fall} → ${erwartet}`, () => {
        expect(needsCurrencyConfirmation({ currencyCode: 'EUR', hasBookings }, { currencyCode: code })).toBe(erwartet);
      });
    });

    it('needsCurrencyConfirmation: beim Anlegen (keine Gruppe) nie', () => {
      expect(needsCurrencyConfirmation(undefined, { currencyCode: 'USD' })).toBeFalse();
    });
  });
});
