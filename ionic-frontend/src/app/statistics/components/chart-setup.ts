import { ArcElement, BarController, BarElement, CategoryScale, Chart, DoughnutController, LinearScale, Tooltip } from 'chart.js';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';

// Nur die benötigten Teile registrieren (Tree-Shaking); Chart.js landet so nur im Statistik-Chunk.
Chart.register(BarController, BarElement, CategoryScale, LinearScale, DoughnutController, ArcElement, Tooltip);

const currency = new ScientificCurrencyPipe();

/** Betrag wie in der übrigen App (`scientificCurrency`), in der Währung der Antwort (DIV-77). */
export function formatAmount(value: number, currencyCode: string): string {
  return currency.transform(value, currencyCode);
}

/** Achsenbeschriftung ohne Nachkommastellen in der Währung der Antwort (DIV-77; Zahlenformat fest `de`). */
export function formatAxisAmount(value: number, currencyCode: string): string {
  return new Intl.NumberFormat('de', { style: 'currency', currency: currencyCode, maximumFractionDigits: 0 }).format(value);
}

export { Chart };
