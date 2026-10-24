import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { environment } from 'src/environments/environment';
import { aLoggedInUser, REFERENCE_DATE } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting } from 'src/testing/test-providers';
import { UserprofileService } from './userprofile.service';

describe('UserprofileService', () => {
  let service: UserprofileService;
  let http: HttpTestingController;
  let alerts: jasmine.SpyObj<AlertService>;
  let auth: jasmine.SpyObj<AuthService>;
  let navigate: jasmine.Spy;
  let user: User;
  const api = `${environment.apiBaseUrl}/api/userprofile`;
  const badRequest = { status: 400, statusText: 'Bad Request' };
  const serverError = { status: 500, statusText: 'Server Error' };

  beforeEach(() => {
    // User.token prüft gegen die aktuelle Uhrzeit – fest auf REFERENCE_DATE, der Testnutzer ist eine Stunde gültig.
    jasmine.clock().install();
    jasmine.clock().mockDate(REFERENCE_DATE);

    user = aLoggedInUser({ id: 5, name: 'Anna', email: 'anna@example.com' });
    alerts = alertServiceSpy();
    auth = authServiceStub(user);
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: AlertService, useValue: alerts },
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(UserprofileService);
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => {
    http.verify();
    jasmine.clock().uninstall();
  });

  function expectLoggedOut() {
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/auth', { replaceUrl: true });
  }

  function expectStillLoggedIn() {
    expect(auth.logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  }

  describe('deleteUserProfile', () => {
    it('meldet den Nutzer nach dem Löschen ab und leitet zur Anmeldung', () => {
      service.deleteUserProfile(5);
      const req = http.expectOne(`${api}/delete/5`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      expectLoggedOut();
    });

    it('lässt den Nutzer bei einem Fehler angemeldet', () => {
      service.deleteUserProfile(5);
      http.expectOne(`${api}/delete/5`).flush('Server error', serverError);

      expectStillLoggedIn();
    });
  });

  describe('changeUserName', () => {
    it('übernimmt den neuen Namen in die Anmeldedaten und behält Token und Ablaufzeit', () => {
      const dto = { id: 5, userName: 'Annika' };

      service.changeUserName(dto);
      const req = http.expectOne(`${api}/update-username`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush({ id: 5, userName: 'Annika', userEmail: 'anna@example.com' });

      expect(auth.setUserData).toHaveBeenCalledOnceWith({
        id: 5,
        name: 'Annika',
        email: 'anna@example.com',
        expirationDate: user.expirationDate,
        token: 'token-5',
      });
    });

    [
      { name: 'Name vergeben', userName: 'Annika', body: 'User with name Annika already exists', key: 'exists' },
      { name: 'Name nicht erlaubt', userName: 'DELETED', body: 'User name is not allowed', key: 'not_allowed' },
    ].forEach(c => {
      it(`zeigt bei einem Fehler (${c.name}) den passenden Hinweis und ändert die Anmeldedaten nicht`, () => {
        service.changeUserName({ id: 5, userName: c.userName });
        http.expectOne(`${api}/update-username`).flush(c.body, badRequest);

        const prefix = `alerts.user_profile.change_username.error_message_service.${c.key}`;
        expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(`${prefix}.header`, `${prefix}.message`);
        expect(auth.setUserData).not.toHaveBeenCalled();
      });
    });

    it('zeigt bei einem anderen Fehler keinen Hinweis', () => {
      service.changeUserName({ id: 5, userName: 'Annika' });
      http.expectOne(`${api}/update-username`).flush('Server error', serverError);

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
      expect(auth.setUserData).not.toHaveBeenCalled();
    });
  });

  describe('changeUserEmail', () => {
    const dto = { id: 5, userName: 'Anna', userEmail: 'neu@example.com' };

    it('meldet den Nutzer nach dem Ändern der E-Mail ab und leitet zur Anmeldung', () => {
      service.changeUserEmail(dto);
      const req = http.expectOne(`${api}/update-usermail`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush(dto);

      expectLoggedOut();
    });

    [
      {
        name: 'E-Mail vergeben', body: 'User with email neu@example.com already exists',
        prefix: 'alerts.user_profile.change_email.error_message_service',
      },
      {
        name: 'ungültige E-Mail', body: 'Invalid Email',
        prefix: 'alerts.user_profile.change_email.error_message_service.invalid_email',
      },
    ].forEach(c => {
      it(`zeigt bei einem Fehler (${c.name}) den passenden Hinweis und lässt den Nutzer angemeldet`, () => {
        service.changeUserEmail(dto);
        http.expectOne(`${api}/update-usermail`).flush(c.body, badRequest);

        expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(`${c.prefix}.header`, `${c.prefix}.message`);
        expectStillLoggedIn();
      });
    });

    it('zeigt bei einem anderen Fehler keinen Hinweis und lässt den Nutzer angemeldet', () => {
      service.changeUserEmail(dto);
      http.expectOne(`${api}/update-usermail`).flush('Server error', serverError);

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
      expectStillLoggedIn();
    });
  });

  describe('changePassword', () => {
    const dto = { userId: 5, oldPassword: 'alt', newPassword: 'neu' };

    it('meldet den Nutzer nach dem Ändern des Passworts ab und leitet zur Anmeldung', () => {
      service.changePassword(dto);
      const req = http.expectOne(`${api}/update-password`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush(null);

      expectLoggedOut();
    });

    it('zeigt bei falschem altem Passwort einen Hinweis und lässt den Nutzer angemeldet', () => {
      service.changePassword(dto);
      http.expectOne(`${api}/update-password`).flush('Incorrect password', badRequest);

      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.user_profile.change_password.error_message_service.header',
        'alerts.user_profile.change_password.error_message_service.message');
      expectStillLoggedIn();
    });

    it('zeigt bei einem anderen Fehler keinen Hinweis und lässt den Nutzer angemeldet', () => {
      service.changePassword(dto);
      http.expectOne(`${api}/update-password`).flush('Server error', serverError);

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
      expectStillLoggedIn();
    });
  });

  describe('resetPassword', () => {
    const dto = { email: 'anna@example.com' };

    it('bestätigt das Zurücksetzen mit einem Hinweis', () => {
      service.resetPassword(dto);
      const req = http.expectOne(`${api}/password-reset`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(dto);
      req.flush(null);

      expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
        'alerts.reset_password.success.header', 'alerts.reset_password.success.message');
    });

    [
      { name: 'unbekannte E-Mail', status: 404, body: 'User with email anna@example.com not found', key: 'error_email_not_exists' },
      { name: 'ungültige E-Mail', status: 400, body: 'Invalid Email', key: 'error_invalid_email' },
    ].forEach(c => {
      it(`zeigt bei einem Fehler (${c.name}) den passenden Hinweis`, () => {
        service.resetPassword(dto);
        http.expectOne(`${api}/password-reset`).flush(c.body, { status: c.status, statusText: 'Error' });

        expect(alerts.showErrorAlert).toHaveBeenCalledOnceWith(
          `alerts.reset_password.${c.key}.header`, `alerts.reset_password.${c.key}.message`);
      });
    });

    it('zeigt bei einem anderen Fehler keinen Hinweis', () => {
      service.resetPassword(dto);
      http.expectOne(`${api}/password-reset`).flush('Server error', serverError);

      expect(alerts.showErrorAlert).not.toHaveBeenCalled();
    });
  });
});
