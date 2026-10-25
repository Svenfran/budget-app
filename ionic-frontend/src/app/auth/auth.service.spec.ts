import { HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { environment } from 'src/environments/environment';
import { REFERENCE_DATE } from 'src/testing/test-data';
import { provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { StorageService } from '../service/storage.service';
import { AuthResponseData, AuthService } from './auth.service';
import { DEVICE_ID } from './device-id';

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
});
