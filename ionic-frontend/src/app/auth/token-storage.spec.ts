import {
  createTokenStorage, MemoryTokenStorage, SecureStoragePort, SecureTokenStorage, SESSION_STORAGE_KEY, StoredSession,
} from './token-storage';

const SESSION: StoredSession = {
  refreshToken: 'refresh-1',
  userId: 7,
  name: 'Anna',
  email: 'anna@example.de',
  sessionExpiresAt: 1757894400000,
};

/** Fake des Secure-Storage-Plugins: Werte als Strings in einer Map, Fehler pro Methode einstellbar. */
function securePortFake() {
  const values = new Map<string, string>();
  const port = {
    values,
    failGet: null as Error | null,
    failRemove: null as Error | null,
    async getItem(key: string) {
      if (port.failGet) throw port.failGet;
      return values.get(key) ?? null;
    },
    async setItem(key: string, value: string) { values.set(key, value); },
    async removeItem(key: string) {
      if (port.failRemove) throw port.failRemove;
      values.delete(key);
    },
  };
  return port;
}

describe('MemoryTokenStorage', () => {
  it('liefert ohne gespeicherte Session null', async () => {
    expect(await new MemoryTokenStorage().load()).toBeNull();
  });

  it('speichert, lädt und löscht die Session', async () => {
    const storage = new MemoryTokenStorage();

    await storage.save(SESSION);
    const geladen = await storage.load();
    await storage.clear();

    expect(geladen).toEqual(SESSION);
    expect(await storage.load()).toBeNull();
  });

  it('gibt Kopien heraus: Änderungen am gespeicherten oder geladenen Objekt wirken nicht auf den Speicher', async () => {
    const storage = new MemoryTokenStorage();
    const original = { ...SESSION };

    await storage.save(original);
    original.refreshToken = 'geaendert';
    const geladen = (await storage.load())!;
    geladen.name = 'Ben';

    expect(await storage.load()).toEqual(SESSION);
  });
});

describe('SecureTokenStorage', () => {
  let port: ReturnType<typeof securePortFake>;
  let storage: SecureTokenStorage;

  beforeEach(() => {
    port = securePortFake();
    storage = new SecureTokenStorage(port);
    spyOn(console, 'warn');
  });

  it('speichert die Session als JSON unter einem festen Schlüssel und lädt sie wieder', async () => {
    await storage.save(SESSION);

    expect(JSON.parse(port.values.get(SESSION_STORAGE_KEY)!)).toEqual(SESSION);
    expect(await storage.load()).toEqual(SESSION);
  });

  it('speichert nur die bekannten Felder', async () => {
    await storage.save({ ...SESSION, accessToken: 'access-1' } as StoredSession);

    expect(Object.keys(JSON.parse(port.values.get(SESSION_STORAGE_KEY)!)).sort())
      .toEqual(['email', 'name', 'refreshToken', 'sessionExpiresAt', 'userId']);
  });

  it('liefert ohne Eintrag null, ohne etwas zu löschen', async () => {
    const remove = spyOn(port, 'removeItem').and.callThrough();

    expect(await storage.load()).toBeNull();
    expect(remove).not.toHaveBeenCalled();
  });

  it('clear entfernt den Eintrag', async () => {
    await storage.save(SESSION);

    await storage.clear();

    expect(port.values.has(SESSION_STORAGE_KEY)).toBeFalse();
  });

  it('Lesefehler (z. B. Keystore-Schlüssel nach Backup-Restore weg): Eintrag gelöscht, null', async () => {
    port.values.set(SESSION_STORAGE_KEY, JSON.stringify(SESSION));
    port.failGet = new Error('decryption failed');

    expect(await storage.load()).toBeNull();
    expect(port.values.has(SESSION_STORAGE_KEY)).toBeFalse();
  });

  it('kein gültiges JSON: Eintrag gelöscht, null', async () => {
    port.values.set(SESSION_STORAGE_KEY, '{kaputt');

    expect(await storage.load()).toBeNull();
    expect(port.values.has(SESSION_STORAGE_KEY)).toBeFalse();
  });

  ([
    ['ohne Refresh Token', { ...SESSION, refreshToken: undefined }],
    ['mit leerem Refresh Token', { ...SESSION, refreshToken: '' }],
    ['ohne Session-Ende', { ...SESSION, sessionExpiresAt: undefined }],
    ['mit userId als Text', { ...SESSION, userId: '7' }],
    ['als Liste', [SESSION]],
    ['als null', null],
  ] as [string, unknown][]).forEach(([fall, inhalt]) => {
    it(`unvollständiger Eintrag (${fall}): gelöscht, null`, async () => {
      port.values.set(SESSION_STORAGE_KEY, JSON.stringify(inhalt));

      expect(await storage.load()).toBeNull();
      expect(port.values.has(SESSION_STORAGE_KEY)).toBeFalse();
    });
  });

  it('scheitert auch das Löschen des unlesbaren Eintrags, bleibt das Ergebnis null', async () => {
    port.failGet = new Error('decryption failed');
    port.failRemove = new Error('io');

    expect(await storage.load()).toBeNull();
  });

  it('loggt beim Verwerfen keinen Inhalt', async () => {
    port.values.set(SESSION_STORAGE_KEY, JSON.stringify({ ...SESSION, userId: 'x' }));

    await storage.load();

    const geloggt = JSON.stringify((console.warn as jasmine.Spy).calls.allArgs());
    expect(geloggt).not.toContain(SESSION.refreshToken);
    expect(geloggt).not.toContain(SESSION.email);
  });

  it('Speicherfehler beim Schreiben werden an den Aufrufer weitergegeben', async () => {
    spyOn(port, 'setItem').and.rejectWith(new Error('io'));

    await expectAsync(storage.save(SESSION)).toBeRejectedWithError('io');
  });
});

describe('createTokenStorage', () => {
  it('wählt im Browser den Arbeitsspeicher', () => {
    expect(createTokenStorage(false)).toEqual(jasmine.any(MemoryTokenStorage));
  });

  it('wählt nativ den Secure Storage mit dem übergebenen Plugin', async () => {
    const port: SecureStoragePort = securePortFake();

    const storage = createTokenStorage(true, port);
    await storage.save(SESSION);

    expect(storage).toEqual(jasmine.any(SecureTokenStorage));
    expect(await port.getItem(SESSION_STORAGE_KEY)).toBe(JSON.stringify(SESSION));
  });
});
