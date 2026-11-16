import { FormControl } from '@angular/forms';
import { Init } from 'src/app/constants/default-values';
import { GroupMembers } from 'src/app/groupmembers/model/groupmembers-dto';
import { aCart, aUser } from 'src/testing/test-data';
import { Zeitraum } from '../new-edit-cart/model/gmh-zeitraum';
import {
  AMOUNT_VALIDATORS, editRecipients, isDaySelectable, settlementEditData, settlementRecipients, toSettlementPaymentDto,
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
  describe('AMOUNT_VALIDATORS (DIV-74: größer als 0, höchstens zwei Nachkommastellen)', () => {
    const errorsOf = (value: unknown) => new FormControl(value, AMOUNT_VALIDATORS).errors;

    [25, 1.5, 0.5, 0.01, 12.34, '25', '1.5', '.5', '0.01', '12.30'].forEach(value => {
      it(`akzeptiert ${JSON.stringify(value)}`, () => {
        expect(errorsOf(value)).toBeNull();
      });
    });

    [0, '0', '0.00', 0.001].forEach(value => {
      it(`lehnt ${JSON.stringify(value)} als nicht größer als 0 ab`, () => {
        const errors = errorsOf(value);
        expect(errors).not.toBeNull();
        expect(errors?.['min'] ?? errors?.['pattern']).toBeDefined();
      });
    });

    [-5, '-5', '+3', 1.234, '1.234', '-0.5'].forEach(value => {
      it(`lehnt ${JSON.stringify(value)} ab (Vorzeichen oder mehr als zwei Nachkommastellen)`, () => {
        expect(errorsOf(value)?.['pattern']).toBeDefined();
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

  describe('Edit-Modus (DIV-74)', () => {
    const ben = aUser({ userName: 'Ben' });

    it('toSettlementPaymentDto trägt im Edit-Modus die Settlement-Id, member bleibt der Empfänger', () => {
      const dto = toSettlementPaymentDto({ amount: 30, memberId: ben.id, datePurchased: '2025-06-10' }, 7, [ben], { settlementId: 70 });

      expect(dto).toEqual({
        id: 70, amount: 30, groupId: 7, member: { id: ben.id, userName: 'Ben' }, datePurchased: new Date('2025-06-10'),
      });
    });

    describe('Richtung „erhalten von“ (Phase 5)', () => {
      const ich = { id: 50, userName: 'Ich' };

      it('erhaltene Zahlung: Gegenpartei wird Zahler (payer), ich bin Empfänger (member)', () => {
        const dto = toSettlementPaymentDto({ amount: 25, memberId: ben.id, datePurchased: '2025-06-10' }, 7, [ben],
          { receivedBy: ich });

        expect(dto).toEqual({
          payer: { id: ben.id, userName: 'Ben' }, amount: 25, groupId: 7, member: ich, datePurchased: new Date('2025-06-10'),
        });
      });

      it('ohne receivedBy bleibt der Request wie bisher (kein payer)', () => {
        const dto = toSettlementPaymentDto({ amount: 25, memberId: ben.id, datePurchased: '2025-06-10' }, 7, [ben]);

        expect(dto.payer).toBeUndefined();
        expect(dto.member).toEqual({ id: ben.id, userName: 'Ben' });
      });

      it('im Edit-Modus zählt die Richtung nicht: member bleibt der Empfänger, kein payer', () => {
        const dto = toSettlementPaymentDto({ amount: 25, memberId: ben.id, datePurchased: '2025-06-10' }, 7, [ben],
          { settlementId: 70, receivedBy: ich });

        expect(dto.payer).toBeUndefined();
        expect(dto.member.id).toBe(ben.id);
        expect(dto.id).toBe(70);
      });
    });

    describe('settlementEditData', () => {
      const zahlerCart = aCart({ id: 1, amount: 30, userDto: anna, settlementId: 70, datePurchased: '2025-06-10' as unknown as Date });
      const empfaengerCart = aCart({ id: 2, amount: -30, userDto: ben, settlementId: 70, datePurchased: '2025-06-10' as unknown as Date });
      const andere = aCart({ id: 3, amount: 12, userDto: anna, settlementId: 71 });

      it('aus dem eigenen Zahler-Cart: Rolle Zahler, Parteien aus dem Paar, Betrag positiv, ISO-Tag', () => {
        expect(settlementEditData(zahlerCart, [andere, empfaengerCart, zahlerCart])).toEqual({
          id: 70, role: 'payer', payer: { id: anna.id, userName: 'Anna' }, receiver: { id: ben.id, userName: 'Ben' },
          amount: 30, datePurchased: '2025-06-10',
        });
      });

      it('aus dem eigenen Empfänger-Cart: Rolle Empfänger, Zahler ist der User des positiven Carts', () => {
        const data = settlementEditData(empfaengerCart, [zahlerCart, empfaengerCart]);

        expect(data?.role).toBe('receiver');
        expect(data?.payer.id).toBe(anna.id);
        expect(data?.receiver.id).toBe(ben.id);
        expect(data?.amount).toBe(30);
      });

      it('nutzt auch einen als gelöscht markierten Partner-Cart (Gegenpartei hat die Gruppe verlassen)', () => {
        const ausgetreten = { ...empfaengerCart, deleted: true };

        expect(settlementEditData(zahlerCart, [zahlerCart, ausgetreten])?.receiver.id).toBe(ben.id);
      });

      it('ohne Partner-Cart (veraltete Liste) oder ohne Settlement: undefined', () => {
        expect(settlementEditData(zahlerCart, [zahlerCart, andere])).toBeUndefined();
        expect(settlementEditData(aCart({ id: 9 }), [aCart({ id: 9 })])).toBeUndefined();
      });
    });

    describe('editRecipients', () => {
      it('lässt die Liste unverändert, wenn der bisherige Empfänger noch auswählbar ist', () => {
        expect(editRecipients([owner, anna], anna)).toEqual([owner, anna]);
      });

      it('ergänzt einen ausgetretenen bisherigen Empfänger, damit er gewählt bleibt', () => {
        expect(editRecipients([owner], anna)).toEqual([owner, anna]);
      });

      it('zeigt den bisherigen Empfänger auch, wenn sonst niemand auswählbar ist', () => {
        expect(editRecipients([], anna)).toEqual([anna]);
      });
    });
  });
});
