import { CategoryDto } from '../model/category-dto';

/** i18n-Key der Settlement-Kategorie (de „Ausgleichszahlung“, en „Repayment“, es „Repago“). */
export const SETTLEMENT_CATEGORY_LABEL_KEY = 'domains.spendings.settlement';

/**
 * Anzeigename einer Kategorie (DIV-74): Die Settlement-Kategorie heißt in jeder Sprache wie in der App eingestellt,
 * unabhängig vom gespeicherten Namen (Sprache des Anlegenden). Erkannt am Kennzeichen; alle anderen Kategorien zeigen
 * ihren gespeicherten Namen. Nur für die Anzeige – gefiltert und gespeichert wird weiter über Name bzw. Id.
 */
export function categoryLabel(category: Pick<CategoryDto, 'name' | 'settlement'>, translate: (key: string) => string): string {
  return category.settlement ? translate(SETTLEMENT_CATEGORY_LABEL_KEY) : category.name;
}
