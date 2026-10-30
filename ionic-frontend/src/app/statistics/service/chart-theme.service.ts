import { Injectable, signal } from '@angular/core';
import { ChartTheme, chartTheme } from '../chart-theme';

/**
 * Aktuelle Diagrammfarben als Signal. Canvas-Farben folgen dem Theme nicht von selbst: Ein MutationObserver auf
 * `body[color-theme]` (gesetzt von `AppComponent.onToggleColorTheme`) liest sie bei jedem Wechsel neu.
 */
@Injectable({
  providedIn: 'root'
})
export class ChartThemeService {

  public theme = signal<ChartTheme>(this.read());

  constructor() {
    new MutationObserver(() => this.theme.set(this.read()))
      .observe(document.body, { attributes: true, attributeFilter: ['color-theme'] });
  }

  private read(): ChartTheme {
    const style = getComputedStyle(document.body);
    return chartTheme(variable => style.getPropertyValue(variable));
  }
}
