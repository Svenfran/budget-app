import { KeyboardOffset } from './keyboard-offset';

describe('KeyboardOffset', () => {
  const VIEWPORT = 800;
  const WIDTH = 400;
  const KEYBOARD = 300;
  let offset: KeyboardOffset;

  beforeEach(() => offset = new KeyboardOffset(VIEWPORT, WIDTH));

  it('verschiebt beim ersten Öffnen erst nach dem Einblenden (Geräteverhalten noch unbekannt)', () => {
    expect(offset.willShow(KEYBOARD)).toBeNull();
  });

  it('liefert die Tastaturhöhe, wenn die WebView nicht verkleinert wurde (Edge-to-Edge, Android 15+)', () => {
    expect(offset.didShow(KEYBOARD, VIEWPORT)).toBe(KEYBOARD);
  });

  it('liefert 0, wenn die WebView um die Tastatur verkleinert wurde (adjustResize, Android ≤ 14)', () => {
    expect(offset.didShow(KEYBOARD, VIEWPORT - KEYBOARD)).toBe(0);
  });

  it('liefert 0 auch dann, wenn die WebView nur um die Tastatur ohne Navigationsleiste schrumpft', () => {
    const navigationBar = 48;

    expect(offset.didShow(KEYBOARD, VIEWPORT - (KEYBOARD - navigationBar))).toBe(0);
  });

  it('verschiebt ab dem zweiten Öffnen sofort, wenn das Gerät nicht verkleinert', () => {
    offset.didShow(KEYBOARD, VIEWPORT);
    offset.didHide(VIEWPORT);

    expect(offset.willShow(KEYBOARD)).toBe(KEYBOARD);
  });

  it('verschiebt ab dem zweiten Öffnen nicht, wenn das Gerät verkleinert', () => {
    offset.didShow(KEYBOARD, VIEWPORT - KEYBOARD);
    offset.didHide(VIEWPORT);

    expect(offset.willShow(KEYBOARD)).toBe(0);
  });

  it('misst bei jedem Einblenden neu; willShow nutzt das zuletzt gemessene Verhalten', () => {
    offset.didShow(KEYBOARD, VIEWPORT);
    offset.didHide(VIEWPORT);

    expect(offset.willShow(KEYBOARD)).toBe(KEYBOARD);
    expect(offset.didShow(KEYBOARD, VIEWPORT - KEYBOARD)).toBe(0);
  });

  it('WebView wird erst nach didShow verkleinert (z. B. Querformat): die folgende Größenänderung setzt den Offset auf 0', () => {
    expect(offset.didShow(KEYBOARD, VIEWPORT)).toBe(KEYBOARD);

    expect(offset.viewportResized(WIDTH, VIEWPORT - KEYBOARD)).toBe(0);
    offset.willHide();
    offset.didHide(VIEWPORT);
    expect(offset.willShow(KEYBOARD)).toBe(0);
  });

  it('Größenänderungen bei geschlossener oder sich schließender Tastatur setzen keinen Offset', () => {
    expect(offset.viewportResized(WIDTH, VIEWPORT - 50)).toBeNull();
    offset.didShow(KEYBOARD, VIEWPORT - KEYBOARD);
    offset.willHide();

    expect(offset.viewportResized(WIDTH, VIEWPORT - KEYBOARD / 2)).toBeNull();
  });

  it('misst ohne Tastaturhöhe nicht und liefert 0', () => {
    expect(offset.didShow(0, VIEWPORT)).toBe(0);
    expect(offset.willShow(KEYBOARD)).toBeNull();
  });

  // Codex-Review 4 zu DIV-2, F-001: Start im Querformat, Drehung vor dem ersten Öffnen
  describe('Drehung', () => {
    const LANDSCAPE = { width: 800, height: 400 };
    const PORTRAIT = { width: 400, height: 800 };

    beforeEach(() => offset = new KeyboardOffset(LANDSCAPE.height, LANDSCAPE.width));

    it('Drehung vor dem ersten Öffnen: verkleinerte WebView wird erkannt, kein Offset, auch beim nächsten Öffnen', () => {
      offset.viewportResized(PORTRAIT.width, PORTRAIT.height);

      expect(offset.didShow(KEYBOARD, PORTRAIT.height - KEYBOARD)).toBe(0);
      offset.didHide(PORTRAIT.height);
      expect(offset.willShow(KEYBOARD)).toBe(0);
    });

    it('Drehung vor dem ersten Öffnen ohne Verkleinerung (Edge-to-Edge): Offset = Tastaturhöhe', () => {
      offset.viewportResized(PORTRAIT.width, PORTRAIT.height);

      expect(offset.didShow(KEYBOARD, PORTRAIT.height)).toBe(KEYBOARD);
    });

    it('eine reine Höhenänderung (Tastatur, gleiche Breite) vor willShow ändert die Höhe ohne Tastatur nicht', () => {
      offset.viewportResized(LANDSCAPE.width, LANDSCAPE.height - 200);

      expect(offset.didShow(200, LANDSCAPE.height - 200)).toBe(0);
    });

    it('Drehung beim ersten Öffnen: keine Einordnung und kein Offset, beim nächsten Öffnen mit neuer Höhe gemessen', () => {
      offset.willShow(KEYBOARD);
      offset.viewportResized(PORTRAIT.width, PORTRAIT.height - KEYBOARD);

      expect(offset.didShow(KEYBOARD, PORTRAIT.height - KEYBOARD)).toBe(0);
      offset.didHide(PORTRAIT.height);
      expect(offset.willShow(KEYBOARD)).toBeNull();
      expect(offset.didShow(KEYBOARD, PORTRAIT.height)).toBe(KEYBOARD);
    });

    it('Drehung bei offener Tastatur nach bereits gemessenem Verhalten: nach dem Schließen wird neu gemessen', () => {
      offset.didShow(KEYBOARD, LANDSCAPE.height - KEYBOARD);
      offset.viewportResized(PORTRAIT.width, PORTRAIT.height - KEYBOARD);
      offset.didHide(PORTRAIT.height);

      expect(offset.willShow(KEYBOARD)).toBeNull();
      expect(offset.didShow(KEYBOARD, PORTRAIT.height - KEYBOARD)).toBe(0);
    });
  });
});
