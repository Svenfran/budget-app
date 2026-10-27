import { accept, acceptFromLoad, begin, confirmAdded, emptyScope, finish, OrderScope, UNKNOWN_REVISION } from './shoppinglist-order';

// Übergänge des Order-Zustands (DIV-1, Übergangstabelle im Plan, Abschnitt 7) ohne TestBed.
describe('shoppinglist-order', () => {
  const scope = (revision: number, ids: number[], pending = 0, failed = false): OrderScope =>
    ({ confirmed: { revision, ids }, pending, failed });

  describe('accept (PUT-Antwort, WebSocket-Event: nur neuere Revision)', () => {
    it('übernimmt eine neuere Revision samt IDs, auch lokal unbekannte', () => {
      expect(accept(scope(3, [1, 2]), 4, [2, 1, 9])).toEqual(scope(4, [2, 1, 9]));
    });

    it('verwirft gleiche und ältere Revisionen (dasselbe Objekt)', () => {
      const current = scope(3, [1, 2]);

      expect(accept(current, 3, [2, 1])).toBe(current);
      expect(accept(current, 2, [2, 1])).toBe(current);
    });

    it('übernimmt gegen UNKNOWN_REVISION auch Revision 0', () => {
      expect(accept(emptyScope(UNKNOWN_REVISION), 0, [5])).toEqual(scope(0, [5]));
    });

    it('lässt offene Requests und Fehlerstatus unberührt', () => {
      expect(accept(scope(1, [1], 2, true), 2, [1])).toEqual(scope(2, [1], 2, true));
    });
  });

  describe('acceptFromLoad (GET: ≥-Regel)', () => {
    it('übernimmt gleiche und neuere Revisionen', () => {
      expect(acceptFromLoad(scope(3, [1, 2]), 3, [2, 1])).toEqual(scope(3, [2, 1]));
      expect(acceptFromLoad(scope(3, [1, 2]), 5, [2, 1])).toEqual(scope(5, [2, 1]));
    });

    it('behält einen neueren bestätigten Stand (Event vor verspäteter GET-Antwort)', () => {
      const current = scope(5, [2, 1]);

      expect(acceptFromLoad(current, 4, [1, 2])).toBe(current);
    });

    it('setzt offene Requests und Fehlerstatus nie zurück', () => {
      expect(acceptFromLoad(scope(1, [1], 1, true), 2, [1])).toEqual(scope(2, [1], 1, true));
    });
  });

  describe('confirmAdded (erfolgreiche Anlage)', () => {
    it('hängt eine fehlende ID an, die Revision bleibt', () => {
      expect(confirmAdded(scope(3, [1, 2]), 7)).toEqual(scope(3, [1, 2, 7]));
    });

    it('behält den Platz einer schon bestätigten ID (Reorder-Event vor dem Add-Event)', () => {
      const current = scope(3, [7, 1, 2]);

      expect(confirmAdded(current, 7)).toBe(current);
    });
  });

  describe('begin/finish (eigene Moves)', () => {
    it('begin zählt einen offenen Request', () => {
      expect(begin(scope(1, [1])).pending).toBe(1);
    });

    it('letzter erfolgreicher Request: reconcile ohne Refetch', () => {
      expect(finish(scope(1, [1], 1), false)).toEqual({ scope: scope(1, [1]), reconcile: true, refetch: false });
    });

    it('letzter Request gescheitert: reconcile und Refetch, failed zurückgesetzt', () => {
      expect(finish(scope(1, [1], 1), true)).toEqual({ scope: scope(1, [1]), reconcile: true, refetch: true });
    });

    it('noch offene Requests: weder reconcile noch Refetch, ein Fehler wird gemerkt', () => {
      expect(finish(scope(1, [1], 2), true)).toEqual({ scope: scope(1, [1], 1, true), reconcile: false, refetch: false });
    });

    it('ein früherer Fehler löst beim erfolgreichen letzten Request den Refetch aus', () => {
      expect(finish(scope(1, [1], 1, true), false)).toEqual({ scope: scope(1, [1]), reconcile: true, refetch: true });
    });
  });
});
