import { AfterViewInit, Component, effect, ElementRef, input, OnDestroy, untracked, viewChild } from '@angular/core';
import { ChartTheme } from '../../chart-theme';
import { ChartThemeService } from '../../service/chart-theme.service';
import { Chart, formatAmount, formatAxisAmount } from '../chart-setup';

/** Balkendiagramm „Von dir bezahlt“; negative Balken (empfangene Ausgleichszahlungen) in der Farbe von danger. */
@Component({
  selector: 'app-bar-chart',
  templateUrl: './bar-chart.component.html',
  styles: [':host { display: block; position: relative; height: 14rem; }'],
  standalone: false
})
export class BarChartComponent implements AfterViewInit, OnDestroy {

  labels = input.required<string[]>();
  values = input.required<number[]>();
  /** Währung der Beträge (DIV-77): Tooltip und Achse */
  currency = input.required<string>();

  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart<'bar'>;

  constructor(private chartThemeService: ChartThemeService) {
    effect(() => {
      const labels = this.labels();
      const values = this.values();
      this.currency(); // Währungswechsel zeichnet Tooltip und Achse neu
      const theme = this.chartThemeService.theme();
      untracked(() => this.chart && this.update(labels, values, theme));
    });
  }

  ngAfterViewInit() {
    const theme = this.chartThemeService.theme();
    this.chart = new Chart(this.canvas().nativeElement, {
      type: 'bar',
      data: { labels: [], datasets: [{ data: [], borderRadius: 4 }] },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: {
          tooltip: { callbacks: { label: context => formatAmount(context.parsed.y ?? 0, this.currency()) } },
        },
        scales: {
          x: { grid: { display: false }, ticks: { maxRotation: 0 } },
          y: { ticks: { callback: value => formatAxisAmount(Number(value), this.currency()) } },
        },
      },
    });
    this.update(this.labels(), this.values(), theme);
  }

  ngOnDestroy() {
    this.chart?.destroy();
  }

  private update(labels: string[], values: number[], theme: ChartTheme) {
    const chart = this.chart!;
    chart.data.labels = labels;
    const dataset = chart.data.datasets[0];
    dataset.data = values;
    dataset.backgroundColor = values.map(value => value < 0 ? theme.negative : theme.positive);
    const scales = chart.options.scales!;
    scales['x']!.ticks!.color = theme.muted;
    scales['y']!.ticks!.color = theme.muted;
    scales['y']!.grid!.color = theme.grid;
    chart.update();
  }
}
