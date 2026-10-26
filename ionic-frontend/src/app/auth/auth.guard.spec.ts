import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, Router, RouterStateSnapshot } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { REFERENCE_DATE, aLoggedInUser } from 'src/testing/test-data';
import { authServiceStub, provideServiceTesting } from 'src/testing/test-providers';
import { routes } from '../app-routing.module';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { User } from './user';

describe('AuthGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let navigateByUrl: jasmine.Spy;

  beforeEach(() => {
    // User.token prüft die aktuelle Zeit – Uhr vor dem Anlegen des Stubs stellen
    jasmine.clock().install();
    jasmine.clock().mockDate(REFERENCE_DATE);
  });

  afterEach(() => jasmine.clock().uninstall());

  function setup(user: User | null): AuthGuard {
    auth = authServiceStub(user);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    return TestBed.inject(AuthGuard);
  }

  const result = (value: unknown) => firstValueFrom(value as Observable<unknown>);
  const canActivate = (guard: AuthGuard) =>
    result(guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  describe('canActivate', () => {
    it('lässt angemeldete Nutzer ohne Auto-Login und ohne Navigation durch', async () => {
      const guard = setup(aLoggedInUser());

      expect(await canActivate(guard)).toBeTrue();
      expect(auth.autoLogin).not.toHaveBeenCalled();
      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('leitet ohne Anmeldung und ohne gespeicherte Anmeldung nach /auth um (z. B. Zurück nach Logout)', async () => {
      const guard = setup(null);

      expect(await canActivate(guard)).toBeFalse();
      expect(auth.autoLogin).toHaveBeenCalledTimes(1);
      expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
    });

    it('lässt Nutzer mit gespeicherter Anmeldung nach Auto-Login durch', async () => {
      const guard = setup(null);
      auth.autoLogin.and.returnValue(of(true));

      expect(await canActivate(guard)).toBeTrue();
      expect(navigateByUrl).not.toHaveBeenCalled();
    });
  });

  it('canLoad leitet ohne Anmeldung und ohne gespeicherte Anmeldung weiterhin nach /auth um', async () => {
    const guard = setup(null);

    expect(await result(guard.canLoad({} as Route, []))).toBeFalse();
    expect(navigateByUrl).toHaveBeenCalledOnceWith('/auth', { replaceUrl: true });
  });

  it('schützt jede per canLoad geschützte Route auch per canActivate, /auth bleibt ungeschützt', () => {
    const guarded = routes.filter(route => route.canLoad?.includes(AuthGuard));
    expect(guarded.length).toBeGreaterThan(0);
    guarded.forEach(route =>
      expect(route.canActivate ?? []).withContext(`Route '${route.path}'`).toContain(AuthGuard));

    const authRoute = routes.find(route => route.path === 'auth');
    expect(authRoute?.canLoad).toBeUndefined();
    expect(authRoute?.canActivate).toBeUndefined();
  });
});
