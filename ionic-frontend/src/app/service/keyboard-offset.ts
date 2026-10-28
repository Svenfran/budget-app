/**
 * Wie viele CSS-Pixel der WebView verdeckt die geöffnete Tastatur? Wird die WebView vom System verkleinert
 * (`adjustResize` ohne erzwungenes Edge-to-Edge, Android ≤ 14), liegen Overlays bereits im freien Bereich → 0.
 * Unter erzwungenem Edge-to-Edge (Android 15+) bleibt die WebView gleich hoch und die Tastatur liegt darüber → ihre Höhe.
 *
 * Ob die WebView verkleinert wurde, zeigt erst die Messung nach dem Einblenden. Gemessen wird bei jedem Einblenden und bei
 * jeder Größenänderung mit offener Tastatur neu: verkleinert das System die WebView erst nach `keyboardDidShow` (z. B. im
 * Querformat), korrigiert die folgende Größenänderung den Offset. Das zuletzt gemessene Verhalten gilt beim nächsten
 * Öffnen schon zu Beginn der Tastatur-Animation.
 *
 * Die Messung braucht die aktuelle Höhe ohne Tastatur, auch nach einer Drehung (Codex-Review 4 zu DIV-2, F-001). Eine
 * Drehung ändert die Breite, die Tastatur nur die Höhe: Größenänderungen mit geänderter Breite bei geschlossener Tastatur
 * werden als neue Höhe ohne Tastatur übernommen. Dreht sich das Gerät bei offener Tastatur, ist die Höhe ohne Tastatur
 * unbekannt: bis zum Schließen kein Offset, danach wird neu gemessen.
 */
export class KeyboardOffset {
  private resizesViewport: boolean | null = null;
  private keyboardOpen = false;
  private keyboardHeight = 0;
  private rotatedWhileOpen = false;

  constructor(private viewportWithoutKeyboard: number, private viewportWidth: number) {}

  /** Tastatur beginnt einzublenden: Offset nach dem zuletzt gemessenen Verhalten, sonst `null` (erst nach dem Einblenden). */
  willShow(keyboardHeight: number): number | null {
    this.keyboardOpen = true;
    this.keyboardHeight = keyboardHeight;
    return this.resizesViewport === null ? null : this.offset();
  }

  /** Tastatur ist eingeblendet: Verhalten messen und Offset liefern. */
  didShow(keyboardHeight: number, viewportNow: number): number {
    this.keyboardOpen = true;
    this.keyboardHeight = keyboardHeight;
    return this.measure(viewportNow);
  }

  /** Tastatur beginnt auszublenden: Größenänderungen ab jetzt nicht mehr messen (die WebView wächst wieder). */
  willHide(): void {
    this.keyboardOpen = false;
  }

  /** Tastatur ist ausgeblendet: Höhe ohne Tastatur neu erfassen; nach einer Drehung bei offener Tastatur neu messen. */
  didHide(viewportNow: number): void {
    this.keyboardOpen = false;
    this.viewportWithoutKeyboard = viewportNow;
    if (this.rotatedWhileOpen) {
      this.resizesViewport = null;
      this.rotatedWhileOpen = false;
    }
  }

  /**
   * Größe der WebView hat sich geändert (`resize`; geänderte Breite = Drehung). Bei offener Tastatur neuer Offset, sonst
   * `null` (nichts zu setzen).
   */
  viewportResized(width: number, height: number): number | null {
    const rotated = width !== this.viewportWidth;
    this.viewportWidth = width;
    if (!this.keyboardOpen) {
      if (rotated) this.viewportWithoutKeyboard = height;
      return null;
    }
    if (rotated) this.rotatedWhileOpen = true;
    return this.measure(height);
  }

  private measure(viewportNow: number): number {
    if (this.rotatedWhileOpen) return 0;
    if (this.keyboardHeight > 0) {
      // Die Tastaturhöhe enthält ggf. die Navigationsleiste, das Schrumpfen der WebView nicht → halbe Höhe als Schwelle
      this.resizesViewport = this.viewportWithoutKeyboard - viewportNow >= this.keyboardHeight / 2;
    }
    return this.offset();
  }

  /** Ohne bekanntes Geräteverhalten kein Offset (der Alert bleibt, wo er ist). */
  private offset(): number {
    return this.resizesViewport !== false || this.keyboardHeight <= 0 ? 0 : this.keyboardHeight;
  }
}
