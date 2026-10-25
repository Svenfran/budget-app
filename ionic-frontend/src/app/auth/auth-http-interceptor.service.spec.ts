import { HTTP_INTERCEPTORS, HttpClient, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { from } from 'rxjs';
import { aLoggedInUser, REFERENCE_DATE } from 'src/testing/test-data';
import { authServiceStub } from 'src/testing/test-providers';
import { AuthHttpInterceptorService } from './auth-http-interceptor.service';
import { AuthService } from './auth.service';
import { User } from './user';

describe('AuthHttpInterceptorService', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: jasmine.SpyObj<AuthService>;
  const url = '/api/groups/sidenav';

  // provideServiceTesting() registriert HttpClient ohne DI-Interceptoren, hier muss der Interceptor greifen
  function setup(user: User | null) {
    // der Stub liest den Token beim Anlegen; User.token prüft den Ablauf gegen die aktuelle Uhr
    jasmine.clock().install();
    jasmine.clock().mockDate(REFERENCE_DATE);
    auth = authServiceStub(user);
    jasmine.clock().uninstall();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideTranslateService(),
        { provide: HTTP_INTERCEPTORS, useClass: AuthHttpInterceptorService, multi: true },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  }

  afterEach(() => http.verify());

  it('Request mit Token wartet auf die DeviceId und setzt danach die Header', fakeAsync(() => {
    const anna = aLoggedInUser();
    setup(anna);
    let resolveDeviceId!: (deviceId: string) => void;
    auth.getDeviceId.and.returnValue(from(new Promise<string>(resolve => resolveDeviceId = resolve)));

    client.get(url).subscribe();
    flushMicrotasks();
    http.expectNone(url);

    resolveDeviceId('device-1');
    flushMicrotasks();
    const req = http.expectOne(url);
    expect(req.request.headers.get('Authorization')).toBe(anna.token);
    expect(req.request.headers.get('DeviceId')).toBe('device-1');
    expect(req.request.headers.get('Accept-Language')).toBe('de');
    req.flush([]);
  }));

  it('Request ohne Token geht sofort und ohne Authorization/DeviceId raus', () => {
    setup(null);

    client.get(url).subscribe();

    const req = http.expectOne(url);
    expect(auth.getDeviceId).not.toHaveBeenCalled();
    expect(req.request.headers.has('Authorization')).toBeFalse();
    expect(req.request.headers.has('DeviceId')).toBeFalse();
    expect(req.request.headers.get('Accept-Language')).toBe('de');
    req.flush([]);
  });
});
