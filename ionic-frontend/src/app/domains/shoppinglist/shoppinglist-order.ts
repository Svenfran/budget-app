/*
 * Order-Zustand eines Bereichs der Einkaufslisten (DIV-1): die Listenreihenfolge der aktiven Gruppe oder die
 * Reihenfolge der Einträge einer Liste. Pure Übergänge laut Übergangstabelle im Plan (Abschnitt 7), ohne Angular.
 *
 * - `confirmed`: vom Server bestätigte Reihenfolge (Revision + vollständige IDs, auch lokal noch fehlende), ergänzt um
 *   erfolgreich angelegte Einträge. Rücksprungstand bei einem Fehler.
 * - `pending`: offene eigene Reorder-Requests; solange > 0 ist die sichtbare Reihenfolge optimistisch.
 * - `failed`: mindestens einer der offenen Requests ist gescheitert; nach dem letzten folgt ein stiller Refetch.
 *
 * Regel: Ohne offene Requests ist die sichtbare Reihenfolge = `confirmed.ids` (tolerant angewandt, `reconcile`).
 */

/** Revision eines Bereichs, für den der Server noch keine geliefert hat (Gruppe ohne Listen). Jede echte ist neuer. */
export const UNKNOWN_REVISION = -1;

export interface OrderScope {
  confirmed: { revision: number; ids: number[] };
  pending: number;
  failed: boolean;
}

export interface FinishResult {
  scope: OrderScope;
  /** Letzter offener Request beendet: sichtbare Reihenfolge auf `confirmed` setzen. */
  reconcile: boolean;
  /** Letzter offener Request beendet und mindestens einer ist gescheitert: still neu laden. */
  refetch: boolean;
}

export function emptyScope(revision: number, ids: number[] = []): OrderScope {
  return { confirmed: { revision, ids }, pending: 0, failed: false };
}

/** Reihenfolge aus einer PUT-Antwort oder einem WebSocket-Event: nur übernehmen, wenn sie neuer ist. */
export function accept(scope: OrderScope, revision: number, ids: number[]): OrderScope {
  if (revision <= scope.confirmed.revision) return scope;
  return { ...scope, confirmed: { revision, ids: [...ids] } };
}

/** Reihenfolge aus dem GET: auch bei gleicher Revision übernehmen (Inhalte sind neu); offene Requests bleiben. */
export function acceptFromLoad(scope: OrderScope, revision: number, ids: number[]): OrderScope {
  if (revision < scope.confirmed.revision) return scope;
  return { ...scope, confirmed: { revision, ids: [...ids] } };
}

/** Erfolgreich angelegter Eintrag: ans Ende, falls er noch fehlt (ein früheres Reorder-Event kennt seinen Platz). */
export function confirmAdded(scope: OrderScope, id: number): OrderScope {
  if (scope.confirmed.ids.includes(id)) return scope;
  return { ...scope, confirmed: { ...scope.confirmed, ids: [...scope.confirmed.ids, id] } };
}

export function begin(scope: OrderScope): OrderScope {
  return { ...scope, pending: scope.pending + 1 };
}

/** Eigener Reorder-Request beendet (Erfolg nach `accept` oder Fehler). */
export function finish(scope: OrderScope, failed: boolean): FinishResult {
  const pending = Math.max(0, scope.pending - 1);
  const anyFailed = scope.failed || failed;
  if (pending > 0) return { scope: { ...scope, pending, failed: anyFailed }, reconcile: false, refetch: false };
  return { scope: { ...scope, pending, failed: false }, reconcile: true, refetch: anyFailed };
}
