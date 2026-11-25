import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TimeoutError } from 'rxjs';
import { environment } from 'src/environments/environment';
import { provideServiceTesting, storageServiceFake, tokenStorageFake } from 'src/testing/test-providers';
import { INIT_NUMBERS } from '../constants/default-values';
import { StorageService } from '../service/storage.service';
import { AuthResponseData, AuthService, NoSessionError, SessionEndedError } from './auth.service';
import { DEVICE_ID } from './device-id';
import { StoredSession, TOKEN_STORAGE } from './token-storage';
import { User } from './user';

const MINUTE = 60 * 1000;
const DAY = 24 * 60 * MINUTE;

describe('AuthService', () => {
  const api = `${environment.apiBaseUrl}/api/auth`;
  let service: AuthService;
  let http: HttpTestingController;
  let storage: ReturnType<typeof tokenStorageFake>;
  let navigateByUrl: jasmine.Spy;
  let deviceId: Promise<string>;
  let previousLegacyToken: string | null;

  /** Antwort von Login/Refresh relativ zur aktuellen (Fake-)Zeit: Access Token 15 min, Session 90 Tage. */
  const response = (overrides: Partial<AuthResponseData> = {}): AuthResponseData => ({
    id: 1, name: 'Anna', email: 'anna@divvy.test',
    token: 'access-1', expirationDate: Date.now() + 15 * MINUTE,
    refreshToken: 'refresh-1', sessionExpirationDate: Date.now() + 90 * DAY,
    ...overrides,
  });

  const stored = (overrides: Partial<StoredSession> = {}): StoredSession => ({
    refreshToken: 'refresh-gespeichert', userId: 1, name: 'Anna', email: 'anna@divvy.test',
    sessionExpiresAt: Date.now() + 30 * DAY, ...overrides,
  });

  function setup(options: { manualStorage?: boolean } = {}) {
    storage = tokenStorageFake({ manual: options.manualStorage });
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: DEVICE_ID, useValue: deviceId },
        { provide: TOKEN_STORAGE, useValue: storage },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  }

  const currentUser = (): User | null => {
    let current: User | null = null;
    service.user.subscribe(user => current = user).unsubscribe();
    return current;
  };

  /** Login mit der Antwort (Fake-Zeit, Speicher automatisch). */
  const login = (resp: AuthResponseData = response()) => {
    service.login('anna@divvy.test', 'geheim123').subscribe();
    flushMicrotasks();
    http.expectOne(`${api}/authenticate`).flush(resp);
    flushMicrotasks();
  };

  /** Startet `getValidAccessToken` und sammelt Ergebnis bzw. Fehler. */
  const requestToken = (minValidity?: number) => {
    const result: { token?: string; error?: unknown } = {};
    service.getValidAccessToken(minValidity).subscribe({ next: t => result.token = t, error: e => result.error = e });
    flushMicrotasks();
    return result;
  };

  const expectRefresh = (): TestRequest => http.expectOne(`${api}/refresh`);

  beforeEach(() => {
    deviceId = Promise.resolve('device-1');
    previousLegacyToken = localStorage.getItem('token');
    spyOn(console, 'warn');
  });

  afterEach(() => {
    http.verify();
    if (previousLegacyToken === null) {
      localStorage.removeItem('token');
    } else {
      localStorage.setItem('token', previousLegacyToken);
    }
  });

  describe('Login und Registrierung', () => {
    let resolveDeviceId: (id: string) => void;

    beforeEach(() => {
      // DeviceId kommt erst, wenn der Test sie freigibt – wie Device.getId() direkt nach dem App-Start
      deviceId = new Promise<string>(resolve => resolveDeviceId = resolve);
      setup();
    });

    it('Login wartet auf die DeviceId und sendet sie mit', fakeAsync(() => {
      service.login('anna@divvy.test', 'geheim123').subscribe();
      flushMicrotasks();
      http.expectNone(`${api}/authenticate`);

      resolveDeviceId('device-1');
      flushMicrotasks();
      const req = http.expectOne(`${api}/authenticate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'anna@divvy.test', password: 'geheim123', deviceId: 'device-1' });
      req.flush(response());
    }));

    it('Registrierung wartet auf die DeviceId und sendet sie mit', fakeAsync(() => {
      service.register('Anna', 'anna@divvy.test', 'geheim123').subscribe();
      flushMicrotasks();
      http.expectNone(`${api}/register`);

      resolveDeviceId('device-1');
      flushMicrotasks();
      const req = http.expectOne(`${api}/register`);
      expect(req.request.body).toEqual(
        { name: 'Anna', email: 'anna@divvy.test', password: 'geheim123', deviceId: 'device-1' });
      req.flush(response());
      flushMicrotasks();

      expect(currentUser()?.id).toBe(1);
      expect(storage.stored()?.refreshToken).toBe('refresh-1');
    }));

    it('Login setzt den Nutzer und speichert den Refresh Token nur im TokenStorage (AC1, AC9)', fakeAsync(() => {
      resolveDeviceId('device-1');
      const resp = response();
      login(resp);
      let authData: { value: string | null } | undefined;
      TestBed.inject(StorageService).getData('authData').then(v => authData = v);
      flushMicrotasks();

      expect(currentUser()).toEqual(new User(1, 'Anna', 'anna@divvy.test', resp.sessionExpirationDate));
      expect(storage.stored()).toEqual({
        refreshToken: 'refresh-1', userId: 1, name: 'Anna', email: 'anna@divvy.test',
        sessionExpiresAt: resp.sessionExpirationDate,
      });
      expect(localStorage.getItem('token')).toBeNull();
      expect(authData?.value).toBeNull();
      expect(service.currentAccessToken()).toBe('access-1');
    }));

    it('Login mit 429 liefert den Fehler, setzt keinen Nutzer und speichert nichts', fakeAsync(() => {
      resolveDeviceId('device-1');
      let error: { status: number } | undefined;
      service.login('anna@divvy.test', 'falsch').subscribe({ error: e => error = e });
      flushMicrotasks();
      http.expectOne(`${api}/authenticate`).flush('Too many login attempts', { status: 429, statusText: 'Too Many Requests' });
      flushMicrotasks();

      expect(error?.status).toBe(429);
      expect(currentUser()).toBeNull();
      expect(storage.calls()).toEqual([]);
    }));
  });

  describe('Verspätete Anmeldeantworten (Review F-001)', () => {
    beforeEach(() => setup());

    /** Startet die Anmeldung, hält die HTTP-Antwort zurück und sammelt Ergebnis bzw. Fehler. */
    const pending = (start: () => ReturnType<AuthService['login']>, url: string) => {
      const result: { response?: AuthResponseData; error?: unknown } = {};
      start().subscribe({ next: r => result.response = r, error: e => result.error = e });
      flushMicrotasks();
      return { result, req: http.expectOne(url) };
    };

    const logoutOhneSession = () => {
      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      flushMicrotasks();
    };

    it('Login-Antwort nach Logout wird verworfen: kein Nutzer, kein Token, nichts gespeichert', fakeAsync(() => {
      const { result, req } = pending(() => service.login('anna@divvy.test', 'geheim123'), `${api}/authenticate`);
      logoutOhneSession();

      req.flush(response());
      flushMicrotasks();

      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(result.response).toBeUndefined();
      expect(currentUser()).toBeNull();
      expect(service.hasSession()).toBeFalse();
      expect(service.currentAccessToken()).toBeNull();
      expect(storage.calls()).toEqual(['clear']);
    }));

    it('Registrierungsantwort nach Logout wird ebenso verworfen', fakeAsync(() => {
      const { result, req } = pending(() => service.register('Anna', 'anna@divvy.test', 'geheim123'), `${api}/register`);
      logoutOhneSession();

      req.flush(response());
      flushMicrotasks();

      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(currentUser()).toBeNull();
      expect(storage.stored()).toBeNull();
    }));

    it('zwei überlappende Logins desselben Nutzers und Geräts: B sendet erst nach A, der zuletzt gestartete gewinnt', fakeAsync(() => {
      const a: { response?: AuthResponseData; error?: unknown } = {};
      const b: { response?: AuthResponseData; error?: unknown } = {};
      service.login('anna@divvy.test', 'geheim123').subscribe({ next: r => a.response = r, error: e => a.error = e });
      service.login('anna@divvy.test', 'geheim123').subscribe({ next: r => b.response = r, error: e => b.error = e });
      flushMicrotasks();

      const reqA = http.expectOne(`${api}/authenticate`);    // B wartet noch: genau ein Request
      reqA.flush(response({ token: 'access-a', refreshToken: 'refresh-a' }));
      flushMicrotasks();
      expect(service.currentAccessToken()).toBe('access-a');

      http.expectOne(`${api}/authenticate`).flush(response({ token: 'access-b', refreshToken: 'refresh-b' }));
      flushMicrotasks();

      expect(a.response?.token).toBe('access-a');
      expect(b.response?.token).toBe('access-b');
      expect(service.currentAccessToken()).toBe('access-b');
      expect(storage.stored()?.refreshToken).toBe('refresh-b');
      expect(storage.calls()).toEqual(['save:refresh-a', 'save:refresh-b']);
    }));

    it('scheitert der erste Login, wird der wartende zweite trotzdem gesendet und übernommen', fakeAsync(() => {
      const a: { error?: { status: number } } = {};
      service.login('anna@divvy.test', 'falsch').subscribe({ error: e => a.error = e });
      service.login('anna@divvy.test', 'geheim123').subscribe();
      flushMicrotasks();

      http.expectOne(`${api}/authenticate`).flush(null, { status: 403, statusText: 'Forbidden' });
      flushMicrotasks();
      http.expectOne(`${api}/authenticate`).flush(response({ token: 'access-b', refreshToken: 'refresh-b' }));
      flushMicrotasks();

      expect(a.error?.status).toBe(403);
      expect(service.currentAccessToken()).toBe('access-b');
    }));

    it('Logout, während ein zweiter Login wartet: der wartende wird nicht gesendet und verworfen', fakeAsync(() => {
      const a: { error?: unknown } = {};
      const b: { error?: unknown } = {};
      service.login('anna@divvy.test', 'geheim123').subscribe({ error: e => a.error = e });
      service.login('anna@divvy.test', 'geheim123').subscribe({ error: e => b.error = e });
      flushMicrotasks();
      const reqA = http.expectOne(`${api}/authenticate`);
      logoutOhneSession();

      reqA.flush(response());
      flushMicrotasks();

      http.expectNone(`${api}/authenticate`);
      expect(a.error).toEqual(jasmine.any(SessionEndedError));
      expect(b.error).toEqual(jasmine.any(SessionEndedError));
      expect(currentUser()).toBeNull();
      expect(storage.stored()).toBeNull();
    }));

    it('ein abbestellter Login gibt die Warteschlange frei', fakeAsync(() => {
      const abo = service.login('anna@divvy.test', 'geheim123').subscribe();
      flushMicrotasks();
      const reqA = http.expectOne(`${api}/authenticate`);
      service.login('anna@divvy.test', 'geheim123').subscribe();
      flushMicrotasks();

      abo.unsubscribe();
      flushMicrotasks();

      expect(reqA.cancelled).toBeTrue();
      http.expectOne(`${api}/authenticate`).flush(response({ token: 'access-b', refreshToken: 'refresh-b' }));
      flushMicrotasks();
      expect(service.currentAccessToken()).toBe('access-b');
    }));

    it('ohne Wechsel dazwischen startet die Antwort die Session wie bisher', fakeAsync(() => {
      const { result, req } = pending(() => service.login('anna@divvy.test', 'geheim123'), `${api}/authenticate`);

      req.flush(response());
      flushMicrotasks();

      expect(result.response?.id).toBe(1);
      expect(currentUser()?.id).toBe(1);
    }));
  });

  describe('Login mit hängendem Speicher', () => {
    beforeEach(() => setup({ manualStorage: true }));

    it('setzt den Nutzer sofort und wartet nicht auf das Speichern', fakeAsync(() => {
      let antwort: AuthResponseData | undefined;
      service.login('anna@divvy.test', 'geheim123').subscribe(r => antwort = r);
      flushMicrotasks();
      http.expectOne(`${api}/authenticate`).flush(response());
      flushMicrotasks();

      expect(antwort?.id).toBe(1);
      expect(currentUser()?.id).toBe(1);
      expect(storage.calls()).toEqual(['save:refresh-1']);
      expect(storage.pendingCount()).toBe(1);
      storage.resolveNext();
      flushMicrotasks();
    }));
  });

  describe('autoLogin (Session-Wiederherstellung)', () => {

    describe('mit sofort antwortendem Speicher', () => {
      beforeEach(() => setup());

      it('stellt eine gespeicherte Session ohne Request wieder her, der Access Token kommt erst später (AC5)', fakeAsync(() => {
        storage.setStored(stored());
        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        expect(ergebnis).toBeTrue();
        expect(currentUser()?.name).toBe('Anna');
        expect(service.hasSession()).toBeTrue();
        expect(service.currentAccessToken()).toBeNull();
        http.expectNone(`${api}/refresh`);
      }));

      it('ohne gespeicherte Session: false', fakeAsync(() => {
        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        expect(ergebnis).toBeFalse();
        expect(currentUser()).toBeNull();
      }));

      it('mit abgelaufener Session: false und der Eintrag wird gelöscht', fakeAsync(() => {
        storage.setStored(stored({ sessionExpiresAt: Date.now() }));
        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        expect(ergebnis).toBeFalse();
        expect(storage.calls()).toEqual(['load', 'clear']);
        expect(storage.stored()).toBeNull();
      }));

      it('entfernt die Anmeldedaten älterer App-Versionen (E2)', fakeAsync(() => {
        const storageService = TestBed.inject(StorageService);
        storageService.setData('authData', JSON.stringify({ token: 'Bearer alt' }));
        localStorage.setItem('token', 'Bearer alt');
        flushMicrotasks();

        service.autoLogin().subscribe();
        flushMicrotasks();
        let authData: { value: string | null } | undefined;
        storageService.getData('authData').then(v => authData = v);
        flushMicrotasks();

        expect(localStorage.getItem('token')).toBeNull();
        expect(authData?.value).toBeNull();
      }));

      it('zwei gleichzeitige Aufrufe (Guard) lesen den Speicher nur einmal', fakeAsync(() => {
        storage.setStored(stored());
        const ergebnisse: boolean[] = [];
        service.autoLogin().subscribe(v => ergebnisse.push(v));
        service.autoLogin().subscribe(v => ergebnisse.push(v));
        flushMicrotasks();

        expect(ergebnisse).toEqual([true, true]);
        expect(storage.calls()).toEqual(['load']);
      }));

      it('nach Logout und neuem Login liefert der Guard true aus dem Arbeitsspeicher, ohne erneut zu lesen', fakeAsync(() => {
        storage.setStored(stored());
        service.autoLogin().subscribe();
        flushMicrotasks();
        service.logout();
        http.expectOne(`${api}/logout`).flush(null);
        login();

        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        expect(ergebnis).toBeTrue();
        expect(storage.calls().filter(c => c === 'load').length).toBe(1);
      }));
    });

    describe('mit hängendem Speicher', () => {
      beforeEach(() => setup({ manualStorage: true }));

      it('liefert nach dem Wartebudget false; die späte Leseantwort wird verworfen', fakeAsync(() => {
        storage.setStored(stored());
        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        tick(INIT_NUMBERS.STORAGE_WAIT_MS);
        expect(ergebnis).toBeFalse();

        storage.resolveNext();
        flushMicrotasks();
        expect(currentUser()).toBeNull();
      }));

      it('Leseantwort nach einem Login in der Zwischenzeit überschreibt den neuen Nutzer nicht', fakeAsync(() => {
        storage.setStored(stored({ userId: 99, name: 'Alt' }));
        service.autoLogin().subscribe();
        flushMicrotasks();
        login(response({ id: 1, name: 'Anna' }));

        storage.resolveNext();          // load
        flushMicrotasks();
        storage.resolveNext();          // save des Logins
        flushMicrotasks();

        expect(currentUser()?.id).toBe(1);
        expect(storage.stored()?.refreshToken).toBe('refresh-1');
      }));

      it('nach einem Logout mit verzögertem clear liest autoLogin nicht erneut und liefert false', fakeAsync(() => {
        login();
        service.logout();
        http.expectOne(`${api}/logout`).flush(null);
        const zugriffeVorher = storage.calls().length;

        let ergebnis: boolean | undefined;
        service.autoLogin().subscribe(v => ergebnis = v);
        flushMicrotasks();

        expect(ergebnis).toBeFalse();
        expect(currentUser()).toBeNull();
        expect(storage.calls().length).toBe(zugriffeVorher);
        storage.resolveNext();
        flushMicrotasks();
        storage.resolveNext();
        flushMicrotasks();
      }));
    });
  });

  describe('Access Token und Refresh', () => {
    beforeEach(() => setup());

    it('ohne Session: NoSessionError, kein Request', fakeAsync(() => {
      const result = requestToken();

      expect(result.error).toEqual(jasmine.any(NoSessionError));
      http.expectNone(`${api}/refresh`);
    }));

    it('gültiger Access Token wird ohne Request geliefert', fakeAsync(() => {
      login();

      expect(requestToken().token).toBe('access-1');
      http.expectNone(`${api}/refresh`);
    }));

    it('abgelaufener Access Token: Refresh mit Refresh Token und DeviceId, neues Paar gespeichert (AC2, AC3)', fakeAsync(() => {
      login();
      tick(15 * MINUTE);

      const result = requestToken();
      const req = expectRefresh();
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1', deviceId: 'device-1' });
      req.flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      expect(result.token).toBe('access-2');
      expect(storage.stored()?.refreshToken).toBe('refresh-2');
      expect(requestToken().token).toBe('access-2');
    }));

    it('Access Token mit zu kurzer Restlaufzeit wird vorab erneuert', fakeAsync(() => {
      login();
      tick(15 * MINUTE - INIT_NUMBERS.ACCESS_TOKEN_MIN_VALIDITY_MS);

      requestToken();

      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
    }));

    it('zwei gleichzeitige Anfragen teilen sich einen Refresh (AC4)', fakeAsync(() => {
      login();
      tick(16 * MINUTE);

      const erste = requestToken();
      const zweite = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      expect(erste.token).toBe('access-2');
      expect(zweite.token).toBe('access-2');
    }));

    it('Refresh mit 401: Session beendet, Speicher geleert, Nutzer null, Navigation zur Anmeldung (AC6)', fakeAsync(() => {
      login();
      tick(16 * MINUTE);

      const result = requestToken();
      expectRefresh().flush({ code: 'REFRESH_TOKEN_INVALID' }, { status: 401, statusText: 'Unauthorized' });
      flushMicrotasks();

      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(currentUser()).toBeNull();
      expect(service.hasSession()).toBeFalse();
      expect(storage.stored()).toBeNull();
      expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
    }));

    [
      ['Netzwerkfehler (Status 0)', 0],
      ['Serverfehler (500)', 500],
      ['Rate-Limit (429)', 429],
    ].forEach(([fall, status]) => {
      it(`Refresh mit ${fall}: Fehler beim Aufrufer, Session bleibt (AC10)`, fakeAsync(() => {
        login();
        tick(16 * MINUTE);

        const result = requestToken();
        expectRefresh().flush(null, { status: status as number, statusText: 'Fehler' });
        flushMicrotasks();

        expect((result.error as HttpErrorResponse).status).toBe(status as number);
        expect(currentUser()?.id).toBe(1);
        expect(storage.stored()?.refreshToken).toBe('refresh-1');
        expect(navigateByUrl).not.toHaveBeenCalled();
      }));
    });

    it('Refresh ohne Antwort: nach 15 s Fehler an alle Wartenden, Session bleibt; der nächste Versuch startet neu', fakeAsync(() => {
      login();
      tick(16 * MINUTE);

      const erste = requestToken();
      const zweite = requestToken();
      const haengend = expectRefresh();
      tick(INIT_NUMBERS.REFRESH_TIMEOUT_MS);

      expect(erste.error).toEqual(jasmine.any(TimeoutError));
      expect(zweite.error).toEqual(jasmine.any(TimeoutError));
      expect(haengend.cancelled).toBeTrue();
      expect(currentUser()?.id).toBe(1);

      const dritte = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      expect(dritte.token).toBe('access-2');
    }));

    it('Refresh-Antwort nach Logout wird verworfen: nichts gespeichert, nichts verteilt', fakeAsync(() => {
      login();
      tick(16 * MINUTE);
      const result = requestToken();
      const refreshReq = expectRefresh();

      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      refreshReq.flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(storage.stored()).toBeNull();
      expect(currentUser()).toBeNull();
      expect(service.currentAccessToken()).toBeNull();
    }));

    it('Refresh-Antwort nach neuem Login wird verworfen, der neue Login bleibt', fakeAsync(() => {
      login();
      tick(16 * MINUTE);
      const result = requestToken();
      const refreshReq = expectRefresh();

      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      login(response({ id: 2, name: 'Ben', token: 'access-ben', refreshToken: 'refresh-ben' }));
      refreshReq.flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(storage.stored()?.refreshToken).toBe('refresh-ben');
      expect(service.currentAccessToken()).toBe('access-ben');
    }));

    it('ein nach Logout beendeter alter Refresh entfernt nicht den Verweis auf den neuen', fakeAsync(() => {
      login();
      tick(16 * MINUTE);
      requestToken();
      const alt = expectRefresh();
      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      login(response({ id: 2, token: 'access-ben', refreshToken: 'refresh-ben', expirationDate: Date.now() }));
      const neu1 = requestToken();
      const neuReq = expectRefresh();

      alt.flush(response({ token: 'access-alt', refreshToken: 'refresh-alt' }));
      flushMicrotasks();
      const neu2 = requestToken();
      http.expectNone(`${api}/refresh`);
      neuReq.flush(response({ token: 'access-ben-2', refreshToken: 'refresh-ben-2' }));
      flushMicrotasks();

      expect(neu1.token).toBe('access-ben-2');
      expect(neu2.token).toBe('access-ben-2');
    }));
  });

  describe('Refresh mit verzögertem Speicher', () => {
    beforeEach(() => setup({ manualStorage: true }));

    /** Login und Speichern des Logins abschließen, dann Access Token ablaufen lassen. */
    const loginAbgeschlossen = () => {
      login();
      storage.resolveNext();
      flushMicrotasks();
      tick(16 * MINUTE);
    };

    it('wartet auf das Speichern und verteilt den Token danach', fakeAsync(() => {
      loginAbgeschlossen();
      const result = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      expect(result.token).toBeUndefined();

      storage.resolveNext();
      flushMicrotasks();
      expect(result.token).toBe('access-2');
      expect(storage.stored()?.refreshToken).toBe('refresh-2');
    }));

    it('hängt das Speichern, gibt es den Token spätestens nach dem Wartebudget; Folgerequests brauchen keinen Refresh', fakeAsync(() => {
      loginAbgeschlossen();
      const result = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      tick(INIT_NUMBERS.STORAGE_WAIT_MS);
      expect(result.token).toBe('access-2');
      expect(requestToken().token).toBe('access-2');
      http.expectNone(`${api}/refresh`);
      storage.resolveNext();
      flushMicrotasks();
    }));

    it('abgelehntes Speichern: Fehler geloggt, Token trotzdem verteilt, ein späteres clear läuft', fakeAsync(() => {
      loginAbgeschlossen();
      const result = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      storage.rejectNext(new Error('io'));
      flushMicrotasks();
      expect(result.token).toBe('access-2');
      expect(console.warn).toHaveBeenCalledWith('Session-Speicher: save fehlgeschlagen', 'Error');

      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      flushMicrotasks();
      storage.resolveNext();
      flushMicrotasks();
      expect(storage.calls()).toEqual(['save:refresh-1', 'save:refresh-2', 'clear']);
    }));

    it('Speichern hängt, dann Logout: am Ende ist der Speicher leer, kein Token verteilt', fakeAsync(() => {
      loginAbgeschlossen();
      const result = requestToken();
      expectRefresh().flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      expect(currentUser()).toBeNull();
      storage.resolveNext();          // save refresh-2 endet
      flushMicrotasks();
      storage.resolveNext();          // clear
      flushMicrotasks();

      expect(result.token).toBeUndefined();
      expect(result.error).toEqual(jasmine.any(SessionEndedError));
      expect(storage.stored()).toBeNull();
    }));

    it('altes Speichern hängt, Logout und neuer Login: am Ende steht nur die neue Session im Speicher', fakeAsync(() => {
      login();                         // save(refresh-1) hängt
      service.logout();
      http.expectOne(`${api}/logout`).flush(null);
      login(response({ id: 2, refreshToken: 'refresh-ben' }));

      storage.resolveNext();
      flushMicrotasks();
      storage.resolveNext();
      flushMicrotasks();
      storage.resolveNext();
      flushMicrotasks();

      expect(storage.calls()).toEqual(['save:refresh-1', 'clear', 'save:refresh-ben']);
      expect(storage.stored()?.refreshToken).toBe('refresh-ben');
    }));

    it('Namensänderung parallel zur Rotation: Speicher enthält neuen Namen und neuen Refresh Token', fakeAsync(() => {
      loginAbgeschlossen();
      requestToken();
      const refreshReq = expectRefresh();
      service.updateUserName('Anna Neu');           // save hängt in der Warteschlange vorne
      refreshReq.flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      storage.resolveNext();
      flushMicrotasks();
      storage.resolveNext();
      flushMicrotasks();

      expect(storage.stored()).toEqual(jasmine.objectContaining({ name: 'Anna Neu', refreshToken: 'refresh-2' }));
    }));
  });

  describe('Logout', () => {
    beforeEach(() => setup({ manualStorage: true }));

    it('räumt sofort lokal auf, auch wenn der Logout-Request nie antwortet und clear hängt (F-008)', fakeAsync(() => {
      login();
      storage.resolveNext();
      flushMicrotasks();

      service.logout();
      flushMicrotasks();

      const req = http.expectOne(`${api}/logout`);
      expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
      expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
      expect(currentUser()).toBeNull();
      expect(service.hasSession()).toBeFalse();
      expect(service.currentAccessToken()).toBeNull();
      expect(storage.calls()).toEqual(['save:refresh-1', 'clear']);
      storage.resolveNext();
      flushMicrotasks();
      req.flush(null);
    }));

    it('ein fehlgeschlagener Logout-Request ändert nichts mehr', fakeAsync(() => {
      login();
      storage.resolveNext();
      flushMicrotasks();

      service.logout();
      http.expectOne(`${api}/logout`).flush(null, { status: 0, statusText: 'Unknown Error' });
      flushMicrotasks();
      storage.resolveNext();
      flushMicrotasks();

      expect(currentUser()).toBeNull();
      expect(storage.stored()).toBeNull();
    }));
  });

  describe('updateUserName und webSocketAuthorization', () => {
    beforeEach(() => setup());

    it('updateUserName ändert Profil und Speicher, ohne Tokens anzufassen', fakeAsync(() => {
      login();

      service.updateUserName('Anna B.');
      flushMicrotasks();

      expect(currentUser()?.name).toBe('Anna B.');
      expect(storage.stored()).toEqual(jasmine.objectContaining({ name: 'Anna B.', refreshToken: 'refresh-1' }));
      expect(service.currentAccessToken()).toBe('access-1');
      http.expectNone(`${api}/refresh`);
    }));

    it('webSocketAuthorization: Token mit "Bearer " bei gültiger Session', fakeAsync(() => {
      login();
      let ergebnis: unknown;
      service.webSocketAuthorization().then(r => ergebnis = r);
      flushMicrotasks();

      expect(ergebnis).toEqual({ kind: 'token', value: 'Bearer access-1' });
    }));

    it('webSocketAuthorization: no-session ohne Session', fakeAsync(() => {
      let ergebnis: unknown;
      service.webSocketAuthorization().then(r => ergebnis = r);
      flushMicrotasks();

      expect(ergebnis).toEqual({ kind: 'no-session' });
    }));

    it('webSocketAuthorization verlangt 60 s Restlaufzeit; Netzwerkfehler beim Refresh ergibt retry', fakeAsync(() => {
      login();
      tick(15 * MINUTE - INIT_NUMBERS.WEBSOCKET_TOKEN_MIN_VALIDITY_MS);
      let ergebnis: unknown;
      service.webSocketAuthorization().then(r => ergebnis = r);
      flushMicrotasks();

      expectRefresh().flush(null, { status: 0, statusText: 'Unknown Error' });
      flushMicrotasks();

      expect(ergebnis).toEqual({ kind: 'retry' });
    }));

    it('webSocketAuthorization: abgelehnter Refresh ergibt no-session', fakeAsync(() => {
      login();
      tick(16 * MINUTE);
      let ergebnis: unknown;
      service.webSocketAuthorization().then(r => ergebnis = r);
      flushMicrotasks();

      expectRefresh().flush(null, { status: 401, statusText: 'Unauthorized' });
      flushMicrotasks();

      expect(ergebnis).toEqual({ kind: 'no-session' });
    }));
  });
});
