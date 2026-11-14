import { FormControl } from '@angular/forms';
import { Init } from 'src/app/constants/default-values';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { aUser } from 'src/testing/test-data';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import {
  AMOUNT_VALIDATORS, isDaySelectable, settlementRecipients, toSettlementPaymentDto,
} from './settlement-form';

describe('settlement-form (DIV-70)', () => {
  const owner = aUser({ userName: 'Olga' });
  const me = aUser({ userName: 'Ich' });
  const anna = aUser({ userName: 'Anna' });

  const groupMembers = (members = [me, anna], ownerUser = owner): GroupMembers =>
    ({ id: 1, name: 'WG', ownerId: ownerUser.id, ownerName: ownerUser.userName, members });

  describe('settlementRecipients', () => {
    it('als Mitglied: Owner zuerst, dann die übrigen Mitglieder, ohne mich', () => {
      expect(settlementRecipients(groupMembers(), me.id))
        .toEqual([{ id: owner.id, userName: 'Olga' }, anna]);
    });

    it('als Owner: alle Mitglieder, ohne mich', () => {
      expect(settlementRecipients(groupMembers([me, anna], owner), owner.id)).toEqual([me, anna]);
    });

    it('keine Empfänger, wenn der Owner allein ist', () => {
      expect(settlementRecipients(groupMembers([]), owner.id)).toEqual([]);
    });

    it('keine Empfänger, solange die Mitglieder noch nicht geladen sind (Platzhalter)', () => {
      expect(settlementRecipients(Init.DEFAULT_GROUP_MEMBERS, me.id)).toEqual([]);
    });
  });

  describe('isDaySelectable', () => {
    const zeitraum = (start: string, end: string | null, overrides: Partial<Zeitraum> = {}): Zeitraum =>
      ({ startDate: start, endDate: end, userId: me.id, groupId: 1, ...overrides }) as unknown as Zeitraum;
    const zeitraeume = [zeitraum('2025-01-10', '2025-01-20'), zeitraum('2025-03-01', null)];

    it('Grenzen eines Zeitraums sind wählbar', () => {
      expect(isDaySelectable('2025-01-10', zeitraeume, me.id, 1)).toBeTrue();
      expect(isDaySelectable('2025-01-20', zeitraeume, me.id, 1)).toBeTrue();
    });

    it('Tage vor dem Beginn und in der Lücke zwischen zwei Zeiträumen sind gesperrt', () => {
      expect(isDaySelectable('2025-01-09', zeitraeume, me.id, 1)).toBeFalse();
      expect(isDaySelectable('2025-01-21', zeitraeume, me.id, 1)).toBeFalse();
      expect(isDaySelectable('2025-02-28', zeitraeume, me.id, 1)).toBeFalse();
    });

    it('offenes Ende: alles ab Beginn ist wählbar', () => {
      expect(isDaySelectable('2025-03-01', zeitraeume, me.id, 1)).toBeTrue();
      expect(isDaySelectable('2026-12-31', zeitraeume, me.id, 1)).toBeTrue();
    });

    it('zählt nur Zeiträume des eigenen Nutzers in dieser Gruppe', () => {
      const fremde = [zeitraum('2025-01-01', null, { userId: anna.id }), zeitraum('2025-01-01', null, { groupId: 2 })];
      expect(isDaySelectable('2025-02-01', fremde, me.id, 1)).toBeFalse();
    });

    it('vergleicht Zeitstempel vom Backend nach UTC-Kalendertag', () => {
      const mitUhrzeit = [zeitraum('2025-01-10T00:00:00.000+00:00', '2025-01-20T00:00:00.000+00:00')];
      expect(isDaySelectable('2025-01-20', mitUhrzeit, me.id, 1)).toBeTrue();
      expect(isDaySelectable('2025-01-21', mitUhrzeit, me.id, 1)).toBeFalse();
    });

    it('ohne geladene Historie ist kein Tag wählbar', () => {
      expect(isDaySelectable('2025-01-10', [], me.id, 1)).toBeFalse();
    });
  });

  describe('toSettlementPaymentDto', () => {
    const longName = aUser({ userName: 'Maximilian Mustermann' });

    it('baut den Request wie bisher: voller Name, Betrag unverändert, Datum als Mitternacht UTC', () => {
      const dto = toSettlementPaymentDto(
        { amount: 25.5, memberId: longName.id, datePurchased: '2025-02-01' }, 7, [longName]);

      expect(dto).toEqual({
        amount: 25.5,
        groupId: 7,
        member: { id: longName.id, userName: 'Maximilian Mustermann' },
        datePurchased: new Date('2025-02-01'),
      });
      expect(dto.datePurchased.toISOString()).toBe('2025-02-01T00:00:00.000Z');
    });

    it('Owner als Empfänger', () => {
      const recipients = settlementRecipients(groupMembers(), me.id);
      const dto = toSettlementPaymentDto({ amount: 10, memberId: owner.id, datePurchased: '2025-02-01' }, 1, recipients);
      expect(dto.member).toEqual({ id: owner.id, userName: 'Olga' });
    });

    it('Empfänger nicht mehr in der Liste: kein Fehler, leerer Name, übrige Felder unverändert', () => {
      const dto = toSettlementPaymentDto({ amount: 10, memberId: 999, datePurchased: '2025-02-01' }, 1, [anna]);
      expect(dto).toEqual({
        amount: 10, groupId: 1, member: { id: 999, userName: '' }, datePurchased: new Date('2025-02-01'),
      });
    });
  });

  // Nur der Validator, nicht der Ionic-Eingabeweg (ion-input type="number" wandelt die Eingabe vorher per parseFloat um).
  describe('AMOUNT_VALIDATORS (bestehende Regeln)', () => {
    const errorsOf = (value: unknown) => new FormControl(value, AMOUNT_VALIDATORS).errors;

    [25, 0, -5, 1.5, 0.5, '25', '0', '-5', '1.5', '.5', '+3'].forEach(value => {
      it(`akzeptiert ${JSON.stringify(value)}`, () => {
        expect(errorsOf(value)).toBeNull();
      });
    });

    [null, ''].forEach(value => {
      it(`verlangt einen Wert (${JSON.stringify(value)})`, () => {
        expect(errorsOf(value)).toEqual(jasmine.objectContaining({ required: true }));
      });
    });

    ['abc', '1,5', '1.', '1.2.3'].forEach(value => {
      it(`lehnt ${JSON.stringify(value)} ab`, () => {
        expect(errorsOf(value)?.['pattern']).toBeDefined();
      });
    });
  });
});
