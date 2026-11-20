import { roundedAmountInput, roundToCents, toCents } from './amount';

// Erwartungen wie im Backend: CostShareCalculator.toCents = BigDecimal.valueOf(amount).setScale(2, HALF_UP) (DIV-77)
describe('amount (DIV-77)', () => {
  describe('toCents', () => {
    ([
      [65.349875, 6535],
      [1.005, 101],
      [-1.005, -101],
      [-0.005, -1],
      [2.675, 268],
      [0.1, 10],
      [12.34, 1234],
      [-3.99, -399],
      [0, 0],
      [1e-7, 0],
      [12345678901.235, 1234567890124],
    ] as [number, number][]).forEach(([amount, cents]) => {
      it(`${amount} → ${cents} Cent`, () => {
        expect(toCents(amount)).toBe(cents);
      });
    });

    it('rundet anders als Math.round(amount * 100) bei Gleitkomma-Grenzfällen', () => {
      expect(Math.round(1.005 * 100)).toBe(100);
      expect(toCents(1.005)).toBe(101);
    });

    it('liefert nie -0', () => {
      expect(Object.is(toCents(-0.004), 0)).toBeTrue();
    });

    it('wirft nicht außerhalb des Backend-Centbereichs (keine Gleichheit mit dem Backend behauptet)', () => {
      expect(() => toCents(1e21)).not.toThrow();
      expect(toCents(1e21)).toBe(1e23);
    });
  });

  describe('roundToCents', () => {
    ([
      [65.349875, 65.35],
      [1.005, 1.01],
      [-1.005, -1.01],
      [-0.005, -0.01],
      [2.675, 2.68],
      [0.1, 0.1],
      [12.34, 12.34],
      [1e-7, 0],
    ] as [number, number][]).forEach(([amount, rounded]) => {
      it(`${amount} → ${rounded}`, () => {
        expect(roundToCents(amount)).toBe(rounded);
      });
    });

    it('-0.004 → 0 (kein -0)', () => {
      expect(Object.is(roundToCents(-0.004), 0)).toBeTrue();
    });
  });

  describe('roundedAmountInput (Eingabe im Betragsfeld, D9)', () => {
    ([
      [65.349875, 65.35],
      ['65.349875', 65.35],
      ['-1.005', -1.01],
      ['+2.675', 2.68],
      ['.125', 0.13],
      ['0.004', 0],
    ] as [unknown, number][]).forEach(([value, rounded]) => {
      it(`rundet ${JSON.stringify(value)} auf ${rounded}`, () => {
        expect(roundedAmountInput(value)).toBe(rounded);
      });
    });

    [12.34, '12.34', '12.3', 12, '-3.99', '', null, undefined, 'abc', '1,005', '1.2.345', '1.', 1e-7].forEach(value => {
      it(`lässt ${JSON.stringify(value) ?? 'undefined'} unverändert (null)`, () => {
        expect(roundedAmountInput(value)).toBeNull();
      });
    });
  });
});
