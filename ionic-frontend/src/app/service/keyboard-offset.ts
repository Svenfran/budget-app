/**
 * Wie viele CSS-Pixel der WebView verdeckt die geöffnete Tastatur? Wird die WebView vom System verkleinert
 * (`adjustResize` ohne erzwungenes Edge-to-Edge, Android ≤ 14), liegen Overlays bereits im freien Bereich → 0.
 * Unter erzwungenem Edge-to-Edge (Android 15+) bleibt die WebView gleich hoch und die Tastatur liegt darüber → ihre Höhe.
 *
 * Ob das Gerät verkleinert, lässt sich erst nach dem Einblenden messen. Das Ergebnis der ersten Messung wird gemerkt,
 * damit ab dem zweiten Öffnen schon zu Beginn der Tastatur-Animation verschoben werden kann.
 */
export class KeyboardOffset {
  private resizesViewport: boolean | null = null;

  constructor(private viewportWithoutKeyboard: number) {}

  /** Tastatur beginnt einzublenden: Offset, sobald das Geräteverhalten bekannt ist, sonst `null` (erst nach dem Einblenden). */
  willShow(keyboardHeight: number): number | null {
    return this.resizesViewport === null ? null : this.offset(keyboardHeight);
  }

  /** Tastatur ist eingeblendet: beim ersten Mal wird das Geräteverhalten gemessen und gemerkt. */
  didShow(keyboardHeight: number, viewportNow: number): number {
    if (this.resizesViewport === null && keyboardHeight > 0) {
      // Die Tastaturhöhe enthält ggf. die Navigationsleiste, das Schrumpfen der WebView nicht → halbe Höhe als Schwelle
      this.resizesViewport = this.viewportWithoutKeyboard - viewportNow >= keyboardHeight / 2;
    }
    return this.offset(keyboardHeight);
  }

  /** Tastatur ist ausgeblendet: Höhe ohne Tastatur neu erfassen (z. B. nach einer Drehung). */
  didHide(viewportNow: number): void {
    this.viewportWithoutKeyboard = viewportNow;
  }

  private offset(keyboardHeight: number): number {
    return this.resizesViewport || keyboardHeight <= 0 ? 0 : keyboardHeight;
  }
}
