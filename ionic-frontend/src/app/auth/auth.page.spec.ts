import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonicModule, LoadingController } from '@ionic/angular';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { throwError, TimeoutError } from 'rxjs';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { AlertService } from '../service/alert.service';
import { LanguageService } from '../service/language.service';
import { StorageService } from '../service/storage.service';
import { AuthPage } from './auth.page';
import { AuthService, SessionEndedError } from './auth.service';

// Fehlerzweig von authenticate(): welcher Alert erscheint (Texte per i18n-Key, TranslateService ohne Loader)
describe('AuthPage – Fehler bei Login', () => {
  let page: AuthPage;
  let auth: jasmine.SpyObj<AuthService>;
  let alerts: jasmine.SpyObj<AlertService>;
  const loading = jasmine.createSpyObj<HTMLIonLoadingElement>('Loading', ['present', 'dismiss']);

  beforeEach(() => {
    auth = authServiceStub(null);
    alerts = alertServiceSpy();
    TestBed.configureTestingModule({
      declarations: [AuthPage],
      imports: [IonicModule.forRoot(), ReactiveFormsModule, TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alerts },
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: LoadingController, useValue: { create: () => Promise.resolve(loading) } },
        { provide: LanguageService, useValue: { currentLang: 'de', setLanguage: () => undefined } },
      ],
    });
    page = TestBed.createComponent(AuthPage).componentInstance;
  });

  function loginFailsWith(error: Partial<HttpErrorResponse>) {
    auth.login.and.returnValue(throwError(() => error));
    page.authenticate('', 'anna@divvy.test', 'geheim123');
    flushMicrotasks();
  }

  it('verworfene Anmeldung (Logout bzw. anderer Login dazwischen): kein Alert, keine Navigation (Review F-001)', fakeAsync(() => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    auth.login.and.returnValue(throwError(() => new SessionEndedError()));

    page.authenticate('', 'anna@divvy.test', 'geheim123');
    flushMicrotasks();

    expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    expect(alerts.showAlertSeverUnavailable).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(loading.dismiss).toHaveBeenCalled();
  }));

  it('keine Antwort im Zeitbudget (TimeoutError): wie Status 0 nur der Server-Alert', fakeAsync(() => {
    auth.login.and.returnValue(throwError(() => new TimeoutError()));

    page.authenticate('', 'anna@divvy.test', 'geheim123');
    flushMicrotasks();

    expect(alerts.showAlertSeverUnavailable).toHaveBeenCalledTimes(1);
    expect(alerts.showErrorAlert).not.toHaveBeenCalled();
  }));

  it('Server nicht erreichbar (Status 0): nur der Server-Alert, kein zweiter Fehler-Alert', fakeAsync(() => {
    loginFailsWith({ status: 0, error: null });

    expect(alerts.showAlertSeverUnavailable).toHaveBeenCalledTimes(1);
    expect(alerts.showErrorAlert).not.toHaveBeenCalled();
  }));

  it('fehlende DeviceId (400, DEVICE_ID_REQUIRED): Fehler-Alert mit der Meldung aus authErrorMessageKey', fakeAsync(() => {
    // Fehlercode-Vertrag X3 (DIV-108)
    loginFailsWith({ status: 400, error: { code: 'DEVICE_ID_REQUIRED', message: 'DeviceId is required' } });

    expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
      'alerts.authenticate.error_messages.header_login',
      'alerts.authenticate.error_messages.message_device_id_missing');
    expect(alerts.showAlertSeverUnavailable).not.toHaveBeenCalled();
  }));

  it('falsches Passwort (403): Fehler-Alert mit Hinweis auf die vorübergehende Sperre', fakeAsync(() => {
    loginFailsWith({ status: 403, error: '' });

    expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
      'alerts.authenticate.error_messages.header_login',
      'alerts.authenticate.error_messages.message_invalid_credentials');
  }));

  // Login-Rate-Limit (DIV-64): Body exakt wie in AuthenticationControllerApiSpec, Retry-After in Sekunden
  it('429 mit Retry-After: Fehler-Alert mit der Restzeit in Minuten', fakeAsync(() => {
    const instant = spyOn(TestBed.inject(TranslateService), 'instant').and.callThrough();

    loginFailsWith({ status: 429, error: 'Too many login attempts', headers: new HttpHeaders({ 'Retry-After': '540' }) });

    expect(instant).toHaveBeenCalledWith('alerts.authenticate.error_messages.message_too_many_attempts_wait', { minutes: 9 });
    expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
      'alerts.authenticate.error_messages.header_login',
      'alerts.authenticate.error_messages.message_too_many_attempts_wait');
  }));

  it('429 ohne lesbaren Retry-After: allgemeine 429-Meldung', fakeAsync(() => {
    loginFailsWith({ status: 429, error: 'Too many login attempts', headers: new HttpHeaders() });

    expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
      'alerts.authenticate.error_messages.header_login',
      'alerts.authenticate.error_messages.message_too_many_attempts');
  }));
});
