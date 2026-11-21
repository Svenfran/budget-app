import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { aCart, aCategory, aUser } from 'src/testing/test-data';
import { duplicateFormValues, duplicateSource } from './duplicate-cart';

describe('duplicateFormValues (DIV-73)', () => {
  it('übernimmt Titel, Betrag mit zwei Nachkommastellen, Beschreibung und Kategorie', () => {
    const cart = aCart({ title: 'Pizza', amount: 12.5, description: 'Freitag', categoryDto: aCategory({ id: 42 }) });

    expect(duplicateFormValues(cart)).toEqual({ title: 'Pizza', amount: '12,50', description: 'Freitag', categoryId: 42 });
  });

  it('macht aus einer fehlenden Beschreibung einen leeren Text', () => {
    const cart = aCart({ description: null as unknown as string });

    expect(duplicateFormValues(cart).description).toBe('');
  });

  it('übernimmt weder ID, Datum, Wiederholung noch Template-Bezug des Originals', () => {
    const cart = aCart({ id: 7, recurrenceType: RECURRENCE_TYPE.MONTHLY, hasActiveTemplate: true, templateId: 3 });

    expect(Object.keys(duplicateFormValues(cart)).sort()).toEqual(['amount', 'categoryId', 'description', 'title']);
  });
});

describe('duplicateSource (DIV-73)', () => {
  const ich = aUser({ userName: 'Anna' });
  const eigener = aCart({ id: 1, groupId: 5, userDto: ich });

  it('liefert einen eigenen, regulären Cart der aktiven Gruppe', () => {
    expect(duplicateSource([eigener], 1, 5, 'Anna')).toBe(eigener);
  });

  it('liefert auch einen wiederkehrenden Cart als Vorlage', () => {
    const wiederkehrend = aCart({ id: 2, groupId: 5, userDto: ich, recurrenceType: RECURRENCE_TYPE.WEEKLY,
      hasActiveTemplate: true });

    expect(duplicateSource([wiederkehrend], 2, 5, 'Anna')).toBe(wiederkehrend);
  });

  const abgelehnt: [string, () => ReturnType<typeof duplicateSource>][] = [
    ['unbekannte ID', () => duplicateSource([eigener], 99, 5, 'Anna')],
    ['Cart eines anderen Mitglieds', () => duplicateSource([eigener], 1, 5, 'Ben')],
    ['ohne angemeldeten Nutzer', () => duplicateSource([eigener], 1, 5, undefined)],
    ['Cart einer anderen Gruppe', () => duplicateSource([eigener], 1, 6, 'Anna')],
    ['Ausgleichszahlung (DIV-74: an settlementId erkannt, Kategoriename egal)', () =>
      duplicateSource([aCart({ id: 7, groupId: 5, userDto: ich, settlementId: 70, categoryDto: aCategory({ name: 'Rückzahlungen' }) })], 7, 5, 'Anna')],
    ['Cart eines entfernten Mitglieds', () =>
      duplicateSource([aCart({ id: 3, groupId: 5, userDto: ich, deleted: true })], 3, 5, 'Anna')],
  ];
  abgelehnt.forEach(([fall, call]) => {
    it(`liefert keine Vorlage: ${fall}`, () => {
      expect(call()).toBeUndefined();
    });
  });

  it('ein unverknüpfter Cart in einer Kategorie namens „Ausgleichszahlung“ ist duplizierbar (DIV-74)', () => {
    const cart = aCart({ id: 4, groupId: 5, userDto: ich, categoryDto: aCategory({ name: 'Ausgleichszahlung' }) });

    expect(duplicateSource([cart], 4, 5, 'Anna')).toBe(cart);
  });
});
