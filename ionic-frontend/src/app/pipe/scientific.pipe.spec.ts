import { SUPPORTED_CURRENCIES } from '../constants/currency';
import { currencySymbol, ScientificCurrencyPipe } from './scientific.pipe';

/**
 * Betragsanzeige je Gruppenwährung (DIV-77), Zahlenformat fest `de`. Erwartungen gemäß ICU von ChromeHeadless; die
 * Android-WebView kann bei einzelnen Symbolen minimal abweichen (Gerätetest durch den Nutzer).
 */
describe('ScientificCurrencyPipe (DIV-77)', () => {
  const pipe = new ScientificCurrencyPipe();
  /** geschütztes Leerzeichen (de: Betrag und Symbol) als normales Leerzeichen, damit die Erwartungen lesbar bleiben */
  const plain = (text: string) => text.replace(/ | /g, ' ');

  const expected: Record<string, string> = {
    EUR: '1.234,50 €',
    USD: '1.234,50 $',
    GBP: '1.234,50 £',
    CHF: '1.234,50 CHF',
    CAD: '1.234,50 CA$',
    AUD: '1.234,50 AU$',
    MXN: '1.234,50 MX$',
    BRL: '1.234,50 R$',
    PLN: '1.234,50 PLN',
    CZK: '1.234,50 CZK',
  };

  SUPPORTED_CURRENCIES.forEach(code => {
    it(`${code}: ${expected[code]}`, () => {
      expect(plain(pipe.transform(1234.5, code))).toBe(expected[code]);
    });
  });

  it('negative Beträge mit Vorzeichen', () => {
    expect(plain(pipe.transform(-20, 'USD'))).toBe('-20,00 $');
  });

  it('ab 1 Mio. wissenschaftlich, mit demselben Symbol wie im Normalfall', () => {
    expect(plain(pipe.transform(1234567.89, 'EUR'))).toBe('1.23 * 10⁶ €');
    expect(plain(pipe.transform(1234567.89, 'BRL'))).toBe('1.23 * 10⁶ R$');
    expect(plain(pipe.transform(-2500000, 'CHF'))).toBe('-2.50 * 10⁶ CHF');
  });

  it('currencySymbol liefert das Symbol der Betragsanzeige', () => {
    expect(SUPPORTED_CURRENCIES.map(code => currencySymbol(code)))
      .toEqual(['€', '$', '£', 'CHF', 'CA$', 'AU$', 'MX$', 'R$', 'PLN', 'CZK']);
  });
});
