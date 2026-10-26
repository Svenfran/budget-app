import { HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from 'src/environments/environment';
import { REFERENCE_DATE } from 'src/testing/test-data';
import { provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { StorageService } from '../service/storage.service';
import { AuthResponseData, AuthService } from './auth.service';
import { DEVICE_ID } from './device-id';
import { User } from './user';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let setUserData: jasmine.Spy;
  let resolveDeviceId: (deviceId: string) => void;
  const api = `${environment.apiBaseUrl}/api/auth`;
  const response: AuthResponseData = {
    id: 1, name: 'Anna', email: 'anna@divvy.test', expirationDate: REFERENCE_DATE.getTime(), token: 'jwt',
  };

  beforeEach(() => {
    // DeviceId kommt erst, wenn der Test sie freigibt – wie Device.getId() direkt nach dem App-Start
    const deviceId = new Promise<string>(resolve => resolveDeviceId = resolve);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: DEVICE_ID, useValue: deviceId },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    // Speichern und Auto-Logout-Timer sind hier nicht Gegenstand
    setUserData = spyOn(service, 'setUserData');
  });

  afterEach(() => http.verify());

  it('Login wartet auf die DeviceId und sendet sie mit', fakeAsync(() => {
    service.login('anna@divvy.test', 'geheim123').subscribe();
    flushMicrotasks();
    http.expectNone(`${api}/authenticate`);

    resolveDeviceId('device-1');
    flushMicrotasks();
    const req = http.expectOne(`${api}/authenticate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'anna@divvy.test', password: 'geheim123', deviceId: 'device-1' });
    req.flush(response);

    expect(setUserData).toHaveBeenCalledWith(response);
  }));

  it('Registrierung wartet auf die DeviceId und sendet sie mit', fakeAsync(() => {
    service.register('Anna', 'anna@divvy.test', 'geheim123').subscribe();
    flushMicrotasks();
    http.expectNone(`${api}/register`);

    resolveDeviceId('device-1');
    flushMicrotasks();
    const req = http.expectOne(`${api}/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(
      { name: 'Anna', email: 'anna@divvy.test', password: 'geheim123', deviceId: 'device-1' });
    req.flush(response);

    expect(setUserData).toHaveBeenCalledWith(response);
  }));

  describe('Auto-Logout', () => {
    const HOUR = 60 * 60 * 1000;
    const expiresInOneHour: AuthResponseData = { ...response, expirationDate: REFERENCE_DATE.getTime() + HOUR };
    let navigateByUrl: jasmine.Spy;
    let previousToken: string | null;

    beforeEach(() => {
      setUserData.and.callThrough();
      // setUserData() schreibt das Token zusätzlich in den echten localStorage des Testbrowsers
      previousToken = localStorage.getItem('token');
      jasmine.clock().install();
      jasmine.clock().mockDate(REFERENCE_DATE);
      navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    });

    afterEach(() => {
      jasmine.clock().uninstall();
      if (previousToken === null) {
        localStorage.removeItem('token');
      } else {
        localStorage.setItem('token', previousToken);
      }
    });

    const currentUser = () => {
      let current: User | null = null;
      service.user.subscribe(user => current = user).unsubscribe();
      return current;
    };

    const expectLogoutRequest = () => http.expectOne(`${api}/logout`).flush(null);

    it('meldet nach Ablauf des Tokens ab und navigiert nach /auth', () => {
      service.setUserData(expiresInOneHour);
      expect(currentUser()).not.toBeNull();

      jasmine.clock().tick(HOUR);

      expectLogoutRequest();
      expect(currentUser()).toBeNull();
      expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
    });

    it('meldet vor Ablauf des Tokens nicht ab', () => {
      service.setUserData(expiresInOneHour);

      jasmine.clock().tick(HOUR - 1);

      http.expectNone(`${api}/logout`);
      expect(currentUser()).not.toBeNull();
      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('navigiert bei manuellem Logout nicht selbst und stoppt den Timer', () => {
      service.setUserData(expiresInOneHour);

      service.logout();
      expectLogoutRequest();
      expect(navigateByUrl).not.toHaveBeenCalled();

      jasmine.clock().tick(2 * HOUR);
      http.expectNone(`${api}/logout`);
      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('ersetzt den Timer bei neuen Nutzerdaten und navigiert erst zum neuen Ablaufzeitpunkt', () => {
      service.setUserData(expiresInOneHour);
      service.setUserData({ ...response, expirationDate: REFERENCE_DATE.getTime() + 2 * HOUR });

      jasmine.clock().tick(HOUR);
      http.expectNone(`${api}/logout`);
      expect(navigateByUrl).not.toHaveBeenCalled();

      jasmine.clock().tick(HOUR);
      expectLogoutRequest();
      expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
    });

    it('armiert den Timer auch beim Auto-Login mit gespeicherter Anmeldung', async () => {
      await TestBed.inject(StorageService).setData('authData', JSON.stringify(
        { ...expiresInOneHour, token: 'Bearer jwt' }));

      expect(await firstValueFrom(service.autoLogin())).toBeTrue();

      jasmine.clock().tick(HOUR);
      expectLogoutRequest();
      expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
    });
  });
});
