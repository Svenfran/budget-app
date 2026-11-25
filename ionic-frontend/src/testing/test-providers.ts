import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { StoredSession, TokenStorage } from 'src/app/auth/token-storage';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { StorageService } from 'src/app/service/storage.service';

/**
 * Standard-Provider für Service-Tests: HTTP über HttpTestingController gemockt, leerer Router,
 * TranslateService ohne Loader – `instant()` liefert den Key zurück, Alerts sind so per Key prüfbar.
 */
export function provideServiceTesting(): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService()];
}

export function alertServiceSpy(): jasmine.SpyObj<AlertService> {
  return jasmine.createSpyObj<AlertService>('AlertService', ['showErrorAlert', 'showToast', 'showAlertSeverUnavailable']);
}

/**
 * In-Memory-Ersatz für den StorageService (statt Capacitor Preferences / localStorage des Testbrowsers,
 * die Zustand zwischen Tests teilen würden). Speichert wie das Original als String bzw. JSON.
 */
export function storageServiceFake(): StorageService {
  const values = new Map<string, string>();
  return {
    async removeData(key: string) { values.delete(key); },
    async setData(key: string, data: string) { values.set(key, JSON.stringify({ data })); },
    async getData(key: string) { return { value: values.get(key) ?? null }; },
    async setItem<T>(key: string, value: T) {
      values.set(key, typeof value === 'string' ? value : JSON.stringify(value));
    },
    async getItem<T>(key: string) {
      const value = values.get(key);
      if (value === undefined) return null;
      try {
        return JSON.parse(value) as T;
      } catch {
        return value as T;
      }
    },
    async removeItem(key: string) { values.delete(key); },
    async clear() { values.clear(); },
  };
}

/**
 * AuthService mit festem Nutzer (`null` = nicht angemeldet); `logout`, `updateUserName`, `getDeviceId`, `login`,
 * `register`, `autoLogin`, `getValidAccessToken`, `refresh`, `webSocketAuthorization`, `hasSession` und
 * `currentAccessToken` sind Spies. `getDeviceId` liefert standardmäßig sofort 'device-1', `autoLogin` standardmäßig
 * `false` (keine gespeicherte Session), `hasSession` je nach Nutzer, `getValidAccessToken` den Token 'access-1'.
 */
export function authServiceStub(user: User | null): jasmine.SpyObj<AuthService> {
  const stub = jasmine.createSpyObj<AuthService>('AuthService',
    ['logout', 'updateUserName', 'getDeviceId', 'login', 'register', 'autoLogin', 'getValidAccessToken', 'refresh',
      'webSocketAuthorization', 'hasSession', 'currentAccessToken'],
    { user: of(user), userIsAuthenticated: of(!!user), authGeneration: 1 });
  stub.getDeviceId.and.returnValue(of('device-1'));
  stub.autoLogin.and.returnValue(of(false));
  stub.hasSession.and.returnValue(!!user);
  stub.getValidAccessToken.and.returnValue(of('access-1'));
  stub.currentAccessToken.and.returnValue(user ? 'access-1' : null);
  stub.webSocketAuthorization.and.resolveTo(user ? { kind: 'token', value: 'Bearer access-1' } : { kind: 'no-session' });
  return stub;
}

/**
 * Session-Speicher für Tests (DIV-65): merkt sich die Aufrufe (`save:<refreshToken>`, `clear`, `load`) und den Stand.
 * Standard: jeder Zugriff ist sofort erledigt. Mit `{ manual: true }` bleibt jeder Zugriff offen, bis der Test ihn mit
 * `resolveNext()` bzw. `rejectNext(err)` beendet (ältester zuerst); erst dann wirkt er auf den Stand.
 */
export function tokenStorageFake(options: { manual?: boolean } = {}) {
  let stored: StoredSession | null = null;
  const calls: string[] = [];
  const pending: { apply: () => unknown; resolve: (value: any) => void; reject: (err: unknown) => void }[] = [];

  const access = <T>(name: string, apply: () => T): Promise<T> => {
    calls.push(name);
    if (!options.manual) {
      return Promise.resolve(apply());
    }
    return new Promise<T>((resolve, reject) => pending.push({ apply, resolve, reject }));
  };

  const fake = {
    load: () => access('load', () => stored && { ...stored }),
    save: (session: StoredSession) => access(`save:${session.refreshToken}`, () => { stored = { ...session }; }),
    clear: () => access('clear', () => { stored = null; }),
    calls: () => [...calls],
    stored: () => stored,
    /** Setzt den Stand direkt (z. B. eine beim letzten App-Lauf gespeicherte Session). */
    setStored: (session: StoredSession | null) => { stored = session && { ...session }; },
    pendingCount: () => pending.length,
    resolveNext: () => {
      const next = pending.shift();
      if (!next) throw new Error('kein offener Speicherzugriff');
      next.resolve(next.apply());
    },
    rejectNext: (err: unknown) => {
      const next = pending.shift();
      if (!next) throw new Error('kein offener Speicherzugriff');
      next.reject(err);
    },
  };
  return fake as typeof fake & TokenStorage;
}
