import { HTTP_INTERCEPTORS, HttpClient, HttpErrorResponse, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { from } from 'rxjs';
import { environment } from 'src/environments/environment';
import { storageServiceFake, tokenStorageFake } from 'src/testing/test-providers';
import { StorageService } from '../service/storage.service';
import { AuthHttpInterceptorService } from './auth-http-interceptor.service';
import { AuthResponseData, AuthService } from './auth.service';
import { DEVICE_ID } from './device-id';
import { TOKEN_STORAGE } from './token-storage';

const MINUTE = 60 * 1000;

/**
 * Interceptor mit echtem AuthService (DIV-65): Nur so lässt sich zeigen, dass sich parallele Requests einen Refresh
 * teilen und verspätete 401 nach einem Wechsel der Anmeldung keinen weiteren Request auslösen.
 */
describe('AuthHttpInterceptorService', () => {
  const base = environment.apiBaseUrl;
  const url = `${base}/api/groups/sidenav`;
  const healthUrl = `${base}/actuator/health`;
  const refreshUrl = `${base}/api/auth/refresh`;
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: AuthService;

  const response = (overrides: Partial<AuthResponseData> = {}): AuthResponseData => ({
    id: 1, name: 'Anna', email: 'anna@divvy.test',
    token: 'access-1', expirationDate: Date.now() + 15 * MINUTE,
    refreshToken: 'refresh-1', sessionExpirationDate: Date.now() + 90 * 24 * 60 * MINUTE,
    ...overrides,
  });

  const unauthorized = { status: 401, statusText: 'Unauthorized' };

  // provideServiceTesting() registriert HttpClient ohne DI-Interceptoren, hier muss der Interceptor greifen
  function setup(deviceId: Promise<string> = Promise.resolve('device-1')) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideTranslateService(),
        provideRouter([]),
        { provide: HTTP_INTERCEPTORS, useClass: AuthHttpInterceptorService, multi: true },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: DEVICE_ID, useValue: deviceId },
        { provide: TOKEN_STORAGE, useValue: tokenStorageFake() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
  }

  function login(resp: AuthResponseData = response()) {
    auth.login(resp.email, 'geheim123').subscribe();
    flushMicrotasks();
    http.expectOne(`${base}/api/auth/authenticate`).flush(resp);
    flushMicrotasks();
  }

  function logout() {
    auth.logout();
    http.expectOne(`${base}/api/auth/logout`).flush(null);
    flushMicrotasks();
  }

  /** GET über den Interceptor; sammelt Antwort bzw. Fehler. */
  function get(target: string = url) {
    const result: { body?: unknown; error?: HttpErrorResponse } = {};
    client.get(target).subscribe({ next: b => result.body = b, error: e => result.error = e });
    flushMicrotasks();
    return result;
  }

  const authorizationOf = (req: TestRequest) => req.request.headers.get('Authorization');

  afterEach(() => http.verify());

  describe('Header', () => {
    it('Request mit Session wartet auf die DeviceId und setzt Token, DeviceId, Sprache und Fehlerformat', fakeAsync(() => {
      let resolveDeviceId!: (id: string) => void;
      setup(new Promise<string>(resolve => resolveDeviceId = resolve));
      resolveDeviceId('device-1');
      login();
      let resolveLater!: (id: string) => void;
      spyOn(auth, 'getDeviceId').and.returnValue(from(new Promise<string>(resolve => resolveLater = resolve)));

      get();
      http.expectNone(url);
      resolveLater('device-1');
      flushMicrotasks();

      const req = http.expectOne(url);
      expect(authorizationOf(req)).toBe('Bearer access-1');
      expect(req.request.headers.get('DeviceId')).toBe('device-1');
      expect(req.request.headers.get('Accept-Language')).toBe('de');
      expect(req.request.headers.get('X-Error-Format')).toBe('json');
      req.flush([]);
    }));

    it('ohne Session geht ein Request sofort und ohne Authorization/DeviceId raus', () => {
      setup();
      const deviceId = spyOn(auth, 'getDeviceId').and.callThrough();

      client.get(url).subscribe();

      const req = http.expectOne(url);
      expect(deviceId).not.toHaveBeenCalled();
      expect(req.request.headers.has('Authorization')).toBeFalse();
      expect(req.request.headers.has('DeviceId')).toBeFalse();
      expect(req.request.headers.get('Accept-Language')).toBe('de');
      // Fehlercodes auch für Login/Registrierung/Passwort-Reset (DIV-108)
      expect(req.request.headers.get('X-Error-Format')).toBe('json');
      req.flush([]);
    });

    it('Health-Check bekommt mit Session den Token, ohne Session keinen', fakeAsync(() => {
      setup();
      get(healthUrl);
      const ohne = http.expectOne(healthUrl);
      expect(ohne.request.headers.has('Authorization')).toBeFalse();
      ohne.flush({ status: 'UP' });

      login();
      get(healthUrl);
      const mit = http.expectOne(healthUrl);
      expect(authorizationOf(mit)).toBe('Bearer access-1');
      mit.flush({ status: 'UP' });
    }));

    [
      ['Auth-Endpunkte', `${base}/api/auth/refresh`],
      ['Assets (i18n)', './assets/i18n/de.json'],
      ['fremde Hosts', 'https://example.org/api/groups'],
    ].forEach(([fall, target]) => {
      it(`${fall} bekommen auch mit Session keinen Token`, fakeAsync(() => {
        setup();
        login();

        get(target);

        const req = http.expectOne(target);
        expect(req.request.headers.has('Authorization')).toBeFalse();
        expect(req.request.headers.get('Accept-Language')).toBe('de');
        req.flush({});
      }));
    });
  });

  describe('Refresh', () => {
    beforeEach(() => setup());

    it('abgelaufener Access Token: erst Refresh, dann der Request mit dem neuen Token (AC2)', fakeAsync(() => {
      login();
      tick(16 * MINUTE);

      const result = get();
      http.expectNone(url);
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();

      const req = http.expectOne(url);
      expect(authorizationOf(req)).toBe('Bearer access-2');
      req.flush(['ok']);
      expect(result.body).toEqual(['ok']);
    }));

    it('401: einmal Refresh, dann Retry mit dem neuen Token (AC2)', fakeAsync(() => {
      login();

      const result = get();
      http.expectOne(url).flush(null, unauthorized);
      flushMicrotasks();
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      const retry = http.expectOne(url);
      expect(authorizationOf(retry)).toBe('Bearer access-2');
      retry.flush(['ok']);

      expect(result.body).toEqual(['ok']);
    }));

    it('Health mit 401: Refresh, Retry, Antwort UP (F-001)', fakeAsync(() => {
      login();

      const result = get(healthUrl);
      http.expectOne(healthUrl).flush(null, unauthorized);
      flushMicrotasks();
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      http.expectOne(healthUrl).flush({ status: 'UP' });

      expect(result.body).toEqual({ status: 'UP' });
    }));

    it('drei parallele Requests mit 401: ein Refresh, drei Retries (AC4)', fakeAsync(() => {
      login();
      const targets = [`${url}?a`, `${url}?b`, `${url}?c`];

      const results = targets.map(t => get(t));
      targets.forEach(t => http.expectOne(t).flush(null, unauthorized));
      flushMicrotasks();
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      targets.forEach(t => {
        const retry = http.expectOne(t);
        expect(authorizationOf(retry)).toBe('Bearer access-2');
        retry.flush('ok');
      });

      expect(results.map(r => r.body)).toEqual(['ok', 'ok', 'ok']);
    }));

    it('verspätete 401 eines mit altem Token gesendeten Requests: Retry mit aktuellem Token, kein zweiter Refresh', fakeAsync(() => {
      login();
      const langsam = get(`${url}?langsam`);
      const langsamReq = http.expectOne(`${url}?langsam`);
      get();
      http.expectOne(url).flush(null, unauthorized);
      flushMicrotasks();
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      http.expectOne(url).flush('ok');

      langsamReq.flush(null, unauthorized);
      flushMicrotasks();

      http.expectNone(refreshUrl);
      const retry = http.expectOne(`${url}?langsam`);
      expect(authorizationOf(retry)).toBe('Bearer access-2');
      retry.flush('ok');
      expect(langsam.body).toBe('ok');
    }));

    it('zweite 401 nach dem Retry: Fehler beim Aufrufer, kein weiterer Refresh', fakeAsync(() => {
      login();

      const result = get();
      http.expectOne(url).flush(null, unauthorized);
      flushMicrotasks();
      http.expectOne(refreshUrl).flush(response({ token: 'access-2', refreshToken: 'refresh-2' }));
      flushMicrotasks();
      http.expectOne(url).flush(null, unauthorized);
      flushMicrotasks();

      http.expectNone(refreshUrl);
      expect(result.error?.status).toBe(401);
    }));

    it('fachliches 403 löst keinen Refresh aus', fakeAsync(() => {
      login();

      const result = get();
      http.expectOne(url).flush({ code: 'NOT_GROUP_MEMBER' }, { status: 403, statusText: 'Forbidden' });
      flushMicrotasks();

      http.expectNone(refreshUrl);
      expect(result.error?.status).toBe(403);
    }));

    it('401 eines Auth-Endpunkts löst keinen Refresh aus (keine Rekursion)', fakeAsync(() => {
      login();

      const result = get(`${base}/api/auth/refresh-test`);
      http.expectOne(`${base}/api/auth/refresh-test`).flush(null, unauthorized);
      flushMicrotasks();

      http.expectNone(refreshUrl);
      expect(result.error?.status).toBe(401);
    }));

    it('Netzwerkfehler beim Refresh: Request scheitert mit Status 0, kein Logout (AC10)', fakeAsync(() => {
      login();
      tick(16 * MINUTE);

      const result = get();
      http.expectOne(refreshUrl).flush(null, { status: 0, statusText: 'Unknown Error' });
      flushMicrotasks();

      http.expectNone(url);
      expect(result.error?.status).toBe(0);
      expect(auth.hasSession()).toBeTrue();
    }));
  });

  describe('Wechsel der Anmeldung (F-001)', () => {
    beforeEach(() => setup());

    it('Request unter A, dann Logout und Login von B, verspätete 401: kein weiterer Request, B bleibt angemeldet', fakeAsync(() => {
      login();
      const result = get();
      const req = http.expectOne(url);
      logout();
      login(response({ id: 2, name: 'Ben', email: 'ben@divvy.test', token: 'access-ben', refreshToken: 'refresh-ben' }));

      req.flush(null, unauthorized);
      flushMicrotasks();

      http.expectNone(refreshUrl);
      http.expectNone(url);
      expect(result.error?.status).toBe(401);
      expect(auth.currentAccessToken()).toBe('access-ben');
      expect(auth.hasSession()).toBeTrue();
    }));

    it('dasselbe bei Logout ohne neuen Login: kein Retry, kein Refresh', fakeAsync(() => {
      login();
      const result = get();
      const req = http.expectOne(url);
      logout();

      req.flush(null, unauthorized);
      flushMicrotasks();

      http.expectNone(refreshUrl);
      http.expectNone(url);
      expect(result.error?.status).toBe(401);
    }));
  });
});
