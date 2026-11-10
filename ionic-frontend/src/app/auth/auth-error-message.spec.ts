import { authErrorMessage, authErrorMessageKey, retryAfterMinutes } from './auth-error-message';

describe('authErrorMessageKey', () => {
  const key = (name: string) => `alerts.authenticate.error_messages.${name}`;
  const EMAIL = 'max@example.de';
  const NAME = 'max';

  // Backend-Texte exakt wie in AuthenticationControllerApiSpec (Fehlermeldungs-Vertrag M5, M6, X3)
  const cases: [string, unknown, number, boolean, string, string, string][] = [
    ['Login ohne DeviceId → eigene Meldung (bisher „Benutzername vergeben“)',
      'DeviceId is required', 400, false, EMAIL, '', 'message_device_id_missing'],
    ['Registrierung ohne DeviceId, Name ist Teil der Meldung → eigene Meldung',
      'DeviceId is required', 400, true, EMAIL, 'required', 'message_device_id_missing'],
    ['Registrierung mit vergebener E-Mail → E-Mail vergeben',
      'User with email max@example.de already exists', 400, true, EMAIL, NAME, 'message_email_exists'],
    ['Registrierung mit vergebenem Namen → Name vergeben',
      'User with name max already exists', 400, true, 'moritz@example.de', NAME, 'message_username_exists'],
    ['Login mit falschem Passwort (403) → allgemeine Meldung',
      '', 403, false, EMAIL, '', 'message'],
    ['Registrierung, 403 mit Namen im Text → allgemeine Meldung',
      'Forbidden for max', 403, true, EMAIL, NAME, 'message'],
    ['Login mit anderem Fehlertext → allgemeine Meldung statt „Benutzername vergeben“',
      'Invalid Email', 400, false, EMAIL, '', 'message'],
    // DIV-64: Body exakt wie in AuthenticationControllerApiSpec, erkannt wird aber am Status
    ['Login 429 (Rate-Limit) → Zu viele Versuche',
      'Too many login attempts', 429, false, EMAIL, '', 'message_too_many_attempts'],
    ['Registrierung 429 → Zu viele Versuche',
      'Too many login attempts', 429, true, EMAIL, NAME, 'message_too_many_attempts'],
    ['429 mit nicht-textuellem Body → Zu viele Versuche',
      { status: 429 }, 429, false, EMAIL, '', 'message_too_many_attempts'],
    ['Body ist kein Text → allgemeine Meldung',
      { status: 500, error: 'Internal Server Error' }, 500, true, EMAIL, NAME, 'message'],
  ];

  cases.forEach(([description, error, status, isRegistration, email, userName, expected]) => {
    it(description, () => {
      expect(authErrorMessageKey(error, status, isRegistration, email, userName)).toBe(key(expected));
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

  it('429 mit Retry-After → Meldung mit Restzeit in Minuten', () => {
    expect(authErrorMessage('Too many login attempts', 429, '540', false, 'max@example.de', ''))
      .toEqual({ key: key('message_too_many_attempts_wait'), params: { minutes: 9 } });
  });

  it('429 ohne lesbaren Retry-After → allgemeine 429-Meldung ohne Parameter', () => {
    expect(authErrorMessage('Too many login attempts', 429, null, false, 'max@example.de', ''))
      .toEqual({ key: key('message_too_many_attempts') });
  });

  it('andere Fehler bleiben unverändert, auch mit Retry-After', () => {
    expect(authErrorMessage('', 403, '900', false, 'max@example.de', ''))
      .toEqual({ key: key('message') });
  });
});
