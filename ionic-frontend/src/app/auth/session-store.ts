import { StoredSession, TokenStorage } from './token-storage';

/** Ergebnis eines Wartens mit Zeitbudget: fertig mit Wert oder Budget überschritten (der Zugriff läuft weiter). */
export type BudgetResult<T> = { done: true; value: T } | { done: false };

/**
 * Wartet höchstens `ms` auf `promise`. Das Budget gilt ab dem Aufruf – direkt nach dem Einreihen aufgerufen also ab dem
 * Einreihen, einschließlich der Wartezeit hinter früheren Einträgen. Bricht den Zugriff selbst nicht ab.
 */
export function withinBudget<T>(promise: Promise<T>, ms: number): Promise<BudgetResult<T>> {
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve({ done: false }), ms);
    promise.then(value => {
      clearTimeout(timer);
      resolve({ done: true, value });
    });
  });
}

/**
 * Serielle Warteschlange vor dem {@link TokenStorage} (DIV-65, „Speicher-Koordination“). Nur vom AuthService genutzt.
 *
 * - Ein Zugriff (`load`, `save`, `clear`) startet erst, wenn der vorherige wirklich beendet ist – es gibt keine
 *   Überlappung nativer Zugriffe und keinen Timeout auf den Zugriff selbst; nur Aufrufer haben ein Zeitbudget
 *   ({@link withinBudget}). So wirken `clear` (Logout) und ein späteres `save` (neuer Login) in Einreihungsreihenfolge.
 * - `save` wird beim Start übersprungen, wenn seine Generation nicht mehr aktuell ist, und schreibt den dann aktuellen
 *   In-Memory-Stand (nicht einen beim Einreihen kopierten). `clear` läuft immer.
 * - Jeder Eintrag fängt seine Fehler selbst ab (Log ohne Token-Daten); die Kette bleibt nie hängen, weil ein Zugriff
 *   abgelehnt wurde. Die zurückgegebenen Promises werden nie abgelehnt.
 */
export class SessionStore {

  private tail: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly storage: TokenStorage,
    /** Ist die Generation noch die aktuelle (kein Logout, Session-Ende oder neuer Login seitdem)? */
    private readonly isCurrent: (generation: number) => boolean,
    /** Aktueller In-Memory-Stand der Session zum Zeitpunkt des Speicherns (`null` = nichts zu speichern). */
    private readonly currentSession: () => StoredSession | null,
  ) {}

  /** Speichert den aktuellen Stand, falls `generation` beim Start noch aktuell ist. */
  save(generation: number): Promise<void> {
    return this.enqueue('save', async () => {
      if (!this.isCurrent(generation)) {
        return;
      }
      const session = this.currentSession();
      if (session) {
        await this.storage.save(session);
      }
    }, undefined);
  }

  clear(): Promise<void> {
    return this.enqueue('clear', () => this.storage.clear(), undefined);
  }

  /** Lädt die gespeicherte Session; bei Fehlern `null`. */
  load(): Promise<StoredSession | null> {
    return this.enqueue('load', () => this.storage.load(), null);
  }

  private enqueue<T>(name: string, access: () => Promise<T>, fallback: T): Promise<T> {
    const entry = this.tail.then(access).catch(err => {
      console.warn(`Session-Speicher: ${name} fehlgeschlagen`, err instanceof Error ? err.name : '');
      return fallback;
    });
    this.tail = entry;
    return entry;
  }
}
