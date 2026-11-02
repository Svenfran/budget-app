import { chartTheme } from './chart-theme';
import { DONUT_MAX_CATEGORIES, DONUT_OTHER_COLOR_INDEX } from './statistics-calc';

describe('chartTheme', () => {
  it('nutzt im Hellmodus für nicht gesetzte Variablen die Standardwerte des Themes', () => {
    const theme = chartTheme(() => '');

    expect(theme.positive).toBe('#457eac');
    expect(theme.negative).toBe('#eb445a');
    expect(theme.text).toBe('#000000');
    expect(theme.muted).toBe('#92949c');
    expect(theme.background).toBe('#ffffff');
  });

  it('übernimmt gesetzte Variablen des Dunkelmodus (getrimmt, wie getPropertyValue sie liefert)', () => {
    const dark: Record<string, string> = {
      '--ion-text-color': ' #ffffff', '--ion-color-step-150': ' #363636', '--ion-item-background': ' #1e1e1e',
      '--ion-color-secondary': ' #50c8ff',
    };

    const theme = chartTheme(variable => dark[variable] ?? '');

    expect(theme.text).toBe('#ffffff');
    expect(theme.grid).toBe('#363636');
    expect(theme.background).toBe('#1e1e1e');
    expect(theme.palette).toContain('#50c8ff');
  });

  it('liefert 8 unterscheidbare Kategoriefarben und „Sonstige“ in der Farbe medium', () => {
    const palette = chartTheme(() => '').palette;

    expect(palette.length).toBe(DONUT_OTHER_COLOR_INDEX + 1);
    expect(new Set(palette.slice(0, DONUT_MAX_CATEGORIES)).size).toBe(DONUT_MAX_CATEGORIES);
    expect(palette[DONUT_OTHER_COLOR_INDEX]).toBe('#92949c');
  });
});
