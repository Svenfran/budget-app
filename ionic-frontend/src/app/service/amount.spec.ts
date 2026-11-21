import { amountFromInput, amountInputText, formattedAmountInput, parseAmountText, roundToCents, toCents } from './amount';

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

  // Phase 9: Eingabefelder mit Komma wie alle Betragsanzeigen (Zahlenformat fest de), Request als Zahl
  describe('amountInputText / amountFromInput', () => {
    it('Feldtext mit Komma und zwei Nachkommastellen', () => {
      expect(amountInputText(42.45)).toBe('42,45');
      expect(amountInputText(12)).toBe('12,00');
      expect(amountInputText(-12)).toBe('-12,00');
      expect(amountInputText(-0.004)).toBe('0,00');
    });

    it('Request-Betrag als Zahl aus Komma-, Punkt- oder Zahleingabe; sonst null', () => {
      expect(amountFromInput('42,45')).toBe(42.45);
      expect(amountFromInput('42.45')).toBe(42.45);
      expect(amountFromInput(42.45)).toBe(42.45);
      expect(amountFromInput('-12,00')).toBe(-12);
      ['', 'abc', null, undefined, NaN].forEach(value => expect(amountFromInput(value)).toBeNull());
    });
  });

  describe('parseAmountText', () => {
    it('liest Punkt und Komma als Dezimaltrenner, auch am Ende; sonst null', () => {
      expect(parseAmountText('45,5')).toBe(45.5);
      expect(parseAmountText('45.')).toBe(45);
      expect(parseAmountText('-0,5')).toBe(-0.5);
      expect(parseAmountText('1.234,5')).toBeNull();
      expect(parseAmountText('')).toBeNull();
    });
  });

  // Betragsfelder (Cart-Formular, Ausgleichszahlung): einheitlich zwei Nachkommastellen (D9, Nutzer-Feedback Phase 9)
  describe('formattedAmountInput (Betragsfelder)', () => {
    ([
      [0, '0,00'],
      ['0.0000', '0,00'],
      [45.40349, '45,40'],
      ['45.40349', '45,40'],
      [12, '12,00'],
      ['12.3', '12,30'],
      ['12.34', '12,34'],
      [-1.005, '-1,01'],
      ['-0.004', '0,00'],
      ['+2.675', '2,68'],
      ['.5', '0,50'],
      [65.349875, '65,35'],
      ['.125', '0,13'],
      // Zahl in Exponentenschreibweise (String(0.0000001) === '1e-7'), Codex F-002
      [0.0000001, '0,00'],
      [-0.0000001, '0,00'],
      [1.23456e-5, '0,00'],
      // Textfeld der Ausgleichszahlung: Komma wie Punkt, Trennzeichen am Ende (Phase 9)
      ['45.', '45,00'],
      ['45,', '45,00'],
      ['45,5', '45,50'],
      ['1,005', '1,01'],
      [' 7,125 ', '7,13'],
    ] as [unknown, string][]).forEach(([value, text]) => {
      it(`${JSON.stringify(value)} → "${text}"`, () => {
        expect(formattedAmountInput(value)).toBe(text);
      });
    });

    ['', '  ', null, undefined, 'abc', '.', ',', '1.2.345', '1,2,3', '1.234,5', '1e-7', NaN, Infinity].forEach(value => {
      it(`lässt ${JSON.stringify(value) ?? 'undefined'} unverändert (null)`, () => {
        expect(formattedAmountInput(value)).toBeNull();
      });
    });
  });

});
