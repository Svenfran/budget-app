import { ArcElement, BarController, BarElement, CategoryScale, Chart, DoughnutController, LinearScale, Tooltip } from 'chart.js';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';

// Nur die benötigten Teile registrieren (Tree-Shaking); Chart.js landet so nur im Statistik-Chunk.
Chart.register(BarController, BarElement, CategoryScale, LinearScale, DoughnutController, ArcElement, Tooltip);

const currency = new ScientificCurrencyPipe();

/** Betrag wie in der übrigen App (`scientificCurrency`). */
export function formatAmount(value: number): string {
  return currency.transform(value);
}

export { Chart };
