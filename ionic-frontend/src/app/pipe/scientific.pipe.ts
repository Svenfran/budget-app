import { Pipe, PipeTransform } from '@angular/core';

/**
 * Währungssymbol eines ISO-Codes in derselben Darstellung wie die Betragsanzeige (`Intl.NumberFormat`, DIV-77),
 * z. B. `EUR → €`, `USD → $`, `CHF → CHF`. Für Labels und Auswahllisten. Das Zahlenformat bleibt vorerst fest `de`.
 */
export function currencySymbol(currencyCode: string, locale = 'de'): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: currencyCode })
    .formatToParts(0)
    .find(part => part.type === 'currency')?.value ?? currencyCode;
}

@Pipe({
  name: 'scientificCurrency'
})
export class ScientificCurrencyPipe implements PipeTransform {

  // Hilfsfunktion, um eine Zahl in hochgestellter Form darzustellen
  private toSuperscript(exponent: number): string {
    const superscriptMap: { [key: string]: string } = {
      '0': '⁰',
      '1': '¹',
      '2': '²',
      '3': '³',
      '4': '⁴',
      '5': '⁵',
      '6': '⁶',
      '7': '⁷',
      '8': '⁸',
      '9': '⁹',
      '-': '⁻'
    };
    return exponent.toString().split('').map(char => superscriptMap[char] || char).join('');
  }

  /**
   * Betrag in der Währung der Gruppe (DIV-77). `currencyCode` ist Pflicht und hat bewusst keinen Standardwert: eine
   * vergessene Anzeige fällt beim Build (strictTemplates) auf, statt still „€“ zu zeigen.
   */
  transform(value: number, currencyCode: string, locale: string = 'de'): string {
    const threshold = 1000000;

    // Wenn der Wert kleiner ist als der Schwellwert, normale Währungsformatierung anwenden
    if (Math.abs(value) < threshold) {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: currencyCode,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(value);
    }

    // Ansonsten wissenschaftliches Format anwenden
    const exponent = Math.floor(Math.log10(Math.abs(value)));
    const mantissa = value / Math.pow(10, exponent);

    // Die Zahl im Format "mantissa * 10^exponent" mit Währungszeichen zurückgeben (Symbol wie im Normalfall)
    const superscriptExponent = this.toSuperscript(exponent);  // Exponent in hochgestellter Form umwandeln

    return `${mantissa.toFixed(2)} * 10${superscriptExponent} ${currencySymbol(currencyCode, locale)}`;
  }
}
