import { InjectionToken } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { SecureStorage } from '@aparajita/capacitor-secure-storage';

/**
 * Was die App nach einem Neustart braucht, um die Session wiederherzustellen (DIV-65): Refresh Token und Profil.
 * Der Access Token wird nie dauerhaft gespeichert, sondern nach dem Start per Refresh geholt.
 */
export interface StoredSession {
  refreshToken: string;
  userId: number;
  name: string;
  email: string;
  /** Absolute Höchstdauer der Session (ms seit Epoch, vom Backend als `sessionExpirationDate`). */
  sessionExpiresAt: number;
}

/**
 * Speicher für die Session (DIV-65, FA5). Implementierungen: {@link SecureTokenStorage} (native, Android Keystore) und
 * {@link MemoryTokenStorage} (Browser/Tests, nur im Arbeitsspeicher). Spätere Plattformen (iOS/PWA) bekommen eine
 * eigene Implementierung hinter derselben Schnittstelle. Fehler beim Lesen führen zu `null` (= neu anmelden).
 */
export abstract class TokenStorage {
  abstract load(): Promise<StoredSession | null>;
  abstract save(session: StoredSession): Promise<void>;
  abstract clear(): Promise<void>;
}

/** Der Teil des Secure-Storage-Plugins, den {@link SecureTokenStorage} nutzt (in Tests durch einen Fake ersetzt). */
export type SecureStoragePort = {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
};

/** Schlüssel im Secure Storage. */
export const SESSION_STORAGE_KEY = 'divvy.session';

/**
 * Speichert die Session verschlüsselt über `@aparajita/capacitor-secure-storage` (Android: Schlüssel im Keystore).
 * Ist der Eintrag nicht lesbar (z. B. nach einem Backup-Restore auf einem neuen Gerät, wo der Keystore-Schlüssel fehlt)
 * oder kein gültiges JSON mit allen Feldern, wird er gelöscht und `null` geliefert.
 */
export class SecureTokenStorage extends TokenStorage {

  constructor(private readonly storage: SecureStoragePort) {
    super();
  }

  async load(): Promise<StoredSession | null> {
    let raw: string | null;
    try {
      raw = await this.storage.getItem(SESSION_STORAGE_KEY);
    } catch (err) {
      return this.discard('nicht lesbar', err);
    }
    if (raw === null) {
      return null;
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      return isStoredSession(parsed) ? copy(parsed) : this.discard('unvollständig');
    } catch (err) {
      return this.discard('kein JSON', err);
    }
  }

  async save(session: StoredSession): Promise<void> {
    await this.storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(copy(session)));
  }

  async clear(): Promise<void> {
    await this.storage.removeItem(SESSION_STORAGE_KEY);
  }

  private async discard(grund: string, err?: unknown): Promise<null> {
    // Nur Grund und Fehlertyp loggen, nie den Inhalt (Refresh Token)
    console.warn(`Gespeicherte Session verworfen: ${grund}`, err instanceof Error ? err.name : '');
    try {
      await this.storage.removeItem(SESSION_STORAGE_KEY);
    } catch {
      // Löschen ist Aufräumen; scheitert es, bleibt das Ergebnis trotzdem "keine Session"
    }
    return null;
  }
}

/**
 * Session nur im Arbeitsspeicher (Browser-Entwicklung mit `npm start`, Tests): Ein Refresh Token landet nie in
 * `localStorage`; nach einem Reload ist ein neuer Login nötig. Gibt Kopien heraus, damit Aufrufer den Speicher nicht
 * über eine geteilte Referenz verändern.
 */
export class MemoryTokenStorage extends TokenStorage {

  private session: StoredSession | null = null;

  async load(): Promise<StoredSession | null> {
    return this.session === null ? null : copy(this.session);
  }

  async save(session: StoredSession): Promise<void> {
    this.session = copy(session);
  }

  async clear(): Promise<void> {
    this.session = null;
  }
}

/** Wählt die Implementierung: nativ der Secure Storage, sonst (Browser) der Arbeitsspeicher. */
export function createTokenStorage(native: boolean, secureStorage: SecureStoragePort = SecureStorage): TokenStorage {
  return native ? new SecureTokenStorage(secureStorage) : new MemoryTokenStorage();
}

/**
 * Session-Speicher der App (DIV-65). Als Token wie `DEVICE_ID`, damit Tests einen steuerbaren Speicher einsetzen können
 * (der Capacitor-Proxy lässt sich nicht zuverlässig per spyOn ersetzen).
 */
export const TOKEN_STORAGE = new InjectionToken<TokenStorage>('TOKEN_STORAGE', {
  providedIn: 'root',
  factory: () => createTokenStorage(Capacitor.isNativePlatform()),
});

function isStoredSession(value: unknown): value is StoredSession {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const v = value as Record<string, unknown>;
  return typeof v['refreshToken'] === 'string' && v['refreshToken'] !== ''
    && typeof v['userId'] === 'number'
    && typeof v['name'] === 'string'
    && typeof v['email'] === 'string'
    && typeof v['sessionExpiresAt'] === 'number';
}

/** Nur die bekannten Felder, als neue Instanz. */
function copy(session: StoredSession): StoredSession {
  return {
    refreshToken: session.refreshToken,
    userId: session.userId,
    name: session.name,
    email: session.email,
    sessionExpiresAt: session.sessionExpiresAt,
  };
}
