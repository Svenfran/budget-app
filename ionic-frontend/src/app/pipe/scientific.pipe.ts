import { Pipe, PipeTransform } from '@angular/core';

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

  transform(value: number, currencyCode: string = 'EUR', display: 'symbol' | 'code' | 'symbol-narrow' = 'symbol', digitsInfo: string = '1.2-2', locale: string = 'de'): string {
    const threshold = 10000000;

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

    // Die Zahl im Format "mantissa * 10^exponent" mit Währungszeichen zurückgeben
    const currencySymbol = currencyCode === 'EUR' ? '€' : currencyCode;
    const superscriptExponent = this.toSuperscript(exponent);  // Exponent in hochgestellter Form umwandeln

    return `${mantissa.toFixed(2)} * 10${superscriptExponent} ${currencySymbol}`;
  }
}
