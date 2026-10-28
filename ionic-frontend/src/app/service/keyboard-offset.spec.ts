import { KeyboardOffset } from './keyboard-offset';

describe('KeyboardOffset', () => {
  const VIEWPORT = 800;
  const KEYBOARD = 300;
  let offset: KeyboardOffset;

  beforeEach(() => offset = new KeyboardOffset(VIEWPORT));

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

  it('misst das Geräteverhalten nur einmal', () => {
    offset.didShow(KEYBOARD, VIEWPORT);
    offset.didHide(VIEWPORT);

    expect(offset.didShow(KEYBOARD, VIEWPORT - KEYBOARD)).toBe(KEYBOARD);
  });

  it('misst ohne Tastaturhöhe nicht und liefert 0', () => {
    expect(offset.didShow(0, VIEWPORT)).toBe(0);
    expect(offset.willShow(KEYBOARD)).toBeNull();
  });
});
