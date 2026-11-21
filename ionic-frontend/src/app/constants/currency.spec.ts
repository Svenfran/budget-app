import { DEFAULT_CURRENCY, isSupportedCurrency, SUPPORTED_CURRENCIES } from './currency';

// Pinnt die Liste (DIV-77); muss mit dem Backend-Enum SupportedCurrency übereinstimmen (SupportedCurrencySpec).
describe('currency (DIV-77)', () => {
  it('unterstützt genau die zwölf Währungen in deren Reihenfolge (Story + NOK, SEK), Standard EUR', () => {
    expect([...SUPPORTED_CURRENCIES]).toEqual(['EUR', 'USD', 'GBP', 'CHF', 'CAD', 'AUD', 'MXN', 'BRL', 'PLN', 'CZK', 'NOK', 'SEK']);
    expect(DEFAULT_CURRENCY).toBe('EUR');
  });

  it('erkennt unterstützte Codes, nur in Großbuchstaben', () => {
    expect(isSupportedCurrency('CHF')).toBeTrue();
    ['usd', 'JPY', '', null, undefined, 1].forEach(code => expect(isSupportedCurrency(code)).toBeFalse());
  });
});
