import { authErrorMessage, authErrorMessageKey, retryAfterMinutes } from './auth-error-message';

describe('authErrorMessageKey', () => {
  const key = (name: string) => `alerts.authenticate.error_messages.${name}`;
  const err = (status: number, error: unknown) => ({ status, error });
  const coded = (status: number, code: string, message: string) => err(status, { code, message });

  // Fehlercodes wie in AuthenticationControllerApiSpec (Vertrag M5, M6, X3, R1; DIV-108)
  const cases: [string, unknown, boolean, string][] = [
    ['Login ohne DeviceId → eigene Meldung',
      coded(400, 'DEVICE_ID_REQUIRED', 'DeviceId is required'), false, 'message_device_id_missing'],
    ['Registrierung ohne DeviceId → eigene Meldung',
      coded(400, 'DEVICE_ID_REQUIRED', 'DeviceId is required'), true, 'message_device_id_missing'],
    ['Registrierung mit vergebener E-Mail → E-Mail vergeben',
      coded(400, 'EMAIL_ALREADY_EXISTS', 'User with email max@example.de already exists'), true, 'message_email_exists'],
    ['Registrierung, E-Mail vergeben durch parallele Registrierung (Text ohne Adresse) → E-Mail vergeben',
      coded(400, 'EMAIL_ALREADY_EXISTS', 'User with email already exists'), true, 'message_email_exists'],
    ['Registrierung mit vergebenem Namen → Name vergeben',
      coded(400, 'USER_NAME_ALREADY_EXISTS', 'User with name max already exists'), true, 'message_username_exists'],
    ['Login mit EMAIL_ALREADY_EXISTS → allgemeine Meldung (nur bei Registrierung relevant)',
      coded(400, 'EMAIL_ALREADY_EXISTS', 'User with email max@example.de already exists'), false, 'message'],
    // DIV-64: falsche Zugangsdaten und Rate-Limit am Status erkannt (Texte/Codes wie in AuthenticationControllerApiSpec)
    ['Login mit falschem Passwort (403 ohne Code) → falsche Zugangsdaten mit Hinweis auf vorübergehende Sperre',
      err(403, ''), false, 'message_invalid_credentials'],
    ['Login 403 mit null-Body → falsche Zugangsdaten mit Hinweis',
      err(403, null), false, 'message_invalid_credentials'],
    ['Registrierung 403 → allgemeine Meldung (kein Sperr-Hinweis)',
      err(403, ''), true, 'message'],
    ['Login 429 mit Code (R1) → Zu viele Versuche',
      coded(429, 'TOO_MANY_LOGIN_ATTEMPTS', 'Too many login attempts'), false, 'message_too_many_attempts'],
    ['Login 429 mit altem Text ohne Code → Zu viele Versuche (Erkennung am Status)',
      err(429, 'Too many login attempts'), false, 'message_too_many_attempts'],
    ['Registrierung 429 → Zu viele Versuche',
      err(429, null), true, 'message_too_many_attempts'],
    ['Login mit anderem Code → allgemeine Meldung',
      coded(400, 'INVALID_EMAIL', 'Invalid Email'), false, 'message'],
    ['nur der Code zählt: geänderter Text → trotzdem DeviceId-Meldung',
      coded(400, 'DEVICE_ID_REQUIRED', 'anderer Text'), false, 'message_device_id_missing'],
    ['alter Text ohne Code → allgemeine Meldung',
      err(400, 'DeviceId is required'), false, 'message'],
    ['Body ohne Code (Spring-Standardfehler) → allgemeine Meldung',
      err(500, { status: 500, error: 'Internal Server Error' }), true, 'message'],
    ['null-Body → allgemeine Meldung',
      err(500, null), true, 'message'],
  ];

  cases.forEach(([description, error, isRegistration, expected]) => {
    it(description, () => {
      expect(authErrorMessageKey(error, isRegistration)).toBe(key(expected));
    });
  });
});

// Restzeit der 429-Meldung aus Retry-After (DIV-64); das Backend sendet Sekunden, z. B. '900'
describe('retryAfterMinutes', () => {
  const cases: [string | null | undefined, number | null][] = [
    ['900', 15], ['899', 15], ['61', 2], ['60', 1], ['1', 1], ['0', 1], [' 120 ', 2],
    [null, null], [undefined, null], ['', null], ['abc', null], ['-5', null], ['1.5', null],
    ['Wed, 21 Oct 2015 07:28:00 GMT', null],
  ];

  cases.forEach(([retryAfter, expected]) => {
    it(`'${retryAfter}' → ${expected}`, () => {
      expect(retryAfterMinutes(retryAfter)).toBe(expected);
    });
  });
});

describe('authErrorMessage', () => {
  const key = (name: string) => `alerts.authenticate.error_messages.${name}`;
  const tooMany = { status: 429, error: { code: 'TOO_MANY_LOGIN_ATTEMPTS', message: 'Too many login attempts' } };

  it('429 mit Retry-After → Meldung mit Restzeit in Minuten', () => {
    expect(authErrorMessage(tooMany, '540', false))
      .toEqual({ key: key('message_too_many_attempts_wait'), params: { minutes: 9 } });
  });

  it('429 ohne lesbaren Retry-After → allgemeine 429-Meldung ohne Parameter', () => {
    expect(authErrorMessage(tooMany, null, false))
      .toEqual({ key: key('message_too_many_attempts') });
  });

  it('andere Fehler bleiben unverändert, auch mit Retry-After', () => {
    expect(authErrorMessage({ status: 403, error: '' }, '900', false))
      .toEqual({ key: key('message_invalid_credentials') });
    expect(authErrorMessage({ status: 400, error: { code: 'DEVICE_ID_REQUIRED', message: 'DeviceId is required' } }, '900', false))
      .toEqual({ key: key('message_device_id_missing') });
  });
});
