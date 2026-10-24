import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
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
  return jasmine.createSpyObj<AlertService>('AlertService', ['showErrorAlert', 'showToast']);
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

/** AuthService mit festem angemeldetem Nutzer; `logout` und `setUserData` sind Spies. */
export function authServiceStub(user: User): jasmine.SpyObj<AuthService> {
  return jasmine.createSpyObj<AuthService>('AuthService', ['logout', 'setUserData'], { user: of(user) });
}
