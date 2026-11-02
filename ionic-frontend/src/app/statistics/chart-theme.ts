/** Farben der Diagramme aus den Ionic-Theme-Variablen (hell/dunkel über `body[color-theme]`). */
export interface ChartTheme {
  positive: string;
  negative: string;
  text: string;
  muted: string;
  grid: string;
  /** Hintergrund der Listen; Trennlinien des Donuts */
  background: string;
  /** 9 Kategoriefarben: Index 0–7 einzelne Segmente, 8 = „Sonstige“ (siehe DONUT_OTHER_COLOR_INDEX) */
  palette: string[];
}

/** Theme-Farben für die Kategorien; ohne Wert gilt der Hellmodus-Standard aus `theme/variables.scss`. */
const PALETTE_VARIABLES: [string, string][] = [
  ['--ion-color-primary', '#457eac'],
  ['--ion-color-success', '#2dd36f'],
  ['--ion-color-warning', '#ffc409'],
  ['--ion-color-danger', '#eb445a'],
  ['--ion-color-tertiary', '#5260ff'],
  ['--ion-color-secondary', '#3dc2ff'],
];
/** Ergänzung auf 8 unterscheidbare Farben, in beiden Modi lesbar */
const EXTRA_COLORS = ['#9b6dd6', '#f08c3a'];

/**
 * Löst die Farben über `read` (z. B. `getComputedStyle(document.body).getPropertyValue`) auf. Im Hellmodus sind Text-
 * und Step-Variablen nicht gesetzt (Ionic nutzt dann interne Standardwerte), daher die Fallbacks.
 */
export function chartTheme(read: (variable: string) => string): ChartTheme {
  const value = (variable: string, fallback: string) => read(variable)?.trim() || fallback;
  const muted = value('--ion-color-medium', '#92949c');
  return {
    positive: value('--ion-color-primary', '#457eac'),
    negative: value('--ion-color-danger', '#eb445a'),
    text: value('--ion-text-color', '#000000'),
    muted,
    grid: value('--ion-color-step-150', 'rgba(146, 148, 156, 0.25)'),
    background: value('--ion-item-background', '#ffffff'),
    palette: [...PALETTE_VARIABLES.map(([variable, fallback]) => value(variable, fallback)), ...EXTRA_COLORS, muted],
  };
}
