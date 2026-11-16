import { aCategory } from 'src/testing/test-data';
import { categoryLabel, SETTLEMENT_CATEGORY_LABEL_KEY } from './category-label';

describe('categoryLabel (DIV-74)', () => {
  const translate = (key: string) => key === SETTLEMENT_CATEGORY_LABEL_KEY ? 'Repayment' : key;

  it('zeigt für die Settlement-Kategorie die übersetzte Bezeichnung, unabhängig vom gespeicherten Namen', () => {
    expect(categoryLabel(aCategory({ name: 'Ausgleichszahlung', settlement: true }), translate)).toBe('Repayment');
    expect(categoryLabel(aCategory({ name: 'Rückzahlungen', settlement: true }), translate)).toBe('Repayment');
  });

  it('zeigt für andere Kategorien den gespeicherten Namen, auch wenn er wie die Settlement-Kategorie heißt', () => {
    expect(categoryLabel(aCategory({ name: 'Lebensmittel' }), translate)).toBe('Lebensmittel');
    expect(categoryLabel(aCategory({ name: 'Ausgleichszahlung' }), translate)).toBe('Ausgleichszahlung');
  });
});
