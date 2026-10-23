import { flattenKeys, missingKeys } from './i18n-keys';

describe('i18n-Schlüsselvergleich', () => {
  it('liefert verschachtelte Schlüssel in Punktnotation', () => {
    const translations = { title: 'Titel', global: { errors: { required: 'Pflichtfeld!' } } };

    expect(flattenKeys(translations)).toEqual(['title', 'global.errors.required']);
  });

  it('meldet fehlende Schlüssel mit vollem Pfad', () => {
    const reference = { global: { errors: { required: 'Pflichtfeld!', invalid_date: 'Datum' } } };
    const candidate = { global: { errors: { required: 'Required!' } } };

    expect(missingKeys(reference, candidate)).toEqual(['global.errors.invalid_date']);
  });

  it('meldet mehrere Abweichungen vollständig und alphabetisch sortiert', () => {
    const reference = {
      zeta: 'z',
      cart: { title: 'Einkauf', amount: 'Betrag' },
      alpha: { nested: { deep: 'tief' } },
      kept: 'bleibt',
    };
    const candidate = { kept: 'stays' };

    expect(missingKeys(reference, candidate))
      .toEqual(['alpha.nested.deep', 'cart.amount', 'cart.title', 'zeta']);
  });

  it('meldet einen Strukturkonflikt (Blatt vs. Objekt) als abweichenden Schlüssel', () => {
    const reference = { menu: { group: 'Gruppe' } };
    const candidate = { menu: 'Menü' };

    expect(missingKeys(reference, candidate)).toEqual(['menu.group']);
    expect(missingKeys(candidate, reference)).toEqual(['menu']);
  });

  it('meldet keine Abweichung bei gleichen Schlüsseln und anderen Werten', () => {
    const reference = { global: { btn_ok: 'OK', btn_cancel: 'Abbrechen' } };
    const candidate = { global: { btn_ok: 'Aceptar', btn_cancel: 'Cancelar' } };

    expect(missingKeys(reference, candidate)).toEqual([]);
    expect(missingKeys(candidate, reference)).toEqual([]);
  });
});
