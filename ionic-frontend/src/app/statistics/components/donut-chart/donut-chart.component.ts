import { AfterViewInit, Component, effect, ElementRef, input, OnDestroy, untracked, viewChild } from '@angular/core';
import { ChartTheme } from '../../chart-theme';
import { DonutSegment } from '../../statistics-calc';
import { ChartThemeService } from '../../service/chart-theme.service';
import { Chart, formatAmount } from '../chart-setup';

/**
 * Donut der Kategorien. Ohne eigene Legende: Die Kategorieliste der Seite ist die Legende (gleiche Farben), das
 * bleibt auch bei vielen Kategorien übersichtlich.
 */
@Component({
  selector: 'app-donut-chart',
  templateUrl: './donut-chart.component.html',
  styles: [':host { display: block; position: relative; height: 13rem; }'],
  standalone: false
})
export class DonutChartComponent implements AfterViewInit, OnDestroy {

  segments = input.required<DonutSegment[]>();
  /** Beschriftung je Segment (Name bzw. übersetztes „Sonstige“) */
  labels = input.required<string[]>();

  private canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private chart?: Chart<'doughnut'>;

  constructor(private chartThemeService: ChartThemeService) {
    effect(() => {
      const segments = this.segments();
      const labels = this.labels();
      const theme = this.chartThemeService.theme();
      untracked(() => this.chart && this.update(segments, labels, theme));
    });
  }

  ngAfterViewInit() {
    this.chart = new Chart(this.canvas().nativeElement, {
      type: 'doughnut',
      data: { labels: [], datasets: [{ data: [], borderWidth: 2 }] },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        cutout: '60%',
        plugins: {
          tooltip: {
            callbacks: {
              label: context => {
                const segment = this.segments()[context.dataIndex];
                return `${formatAmount(segment.amount)} (${segment.percent.toLocaleString('de')} %)`;
              },
            },
          },
        },
      },
    });
    this.update(this.segments(), this.labels(), this.chartThemeService.theme());
  }

  ngOnDestroy() {
    this.chart?.destroy();
  }

  private update(segments: DonutSegment[], labels: string[], theme: ChartTheme) {
    const chart = this.chart!;
    chart.data.labels = labels;
    const dataset = chart.data.datasets[0];
    dataset.data = segments.map(segment => segment.amount);
    dataset.backgroundColor = segments.map(segment => theme.palette[segment.colorIndex]);
    // Trennlinien in Hintergrundfarbe, damit die Segmente in beiden Modi abgesetzt sind
    dataset.borderColor = theme.background;
    chart.update();
  }
}
