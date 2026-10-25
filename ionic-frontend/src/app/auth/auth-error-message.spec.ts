import { authErrorMessageKey } from './auth-error-message';

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
    ['Body ist kein Text → allgemeine Meldung',
      { status: 500, error: 'Internal Server Error' }, 500, true, EMAIL, NAME, 'message'],
  ];

  cases.forEach(([description, error, status, isRegistration, email, userName, expected]) => {
    it(description, () => {
      expect(authErrorMessageKey(error, status, isRegistration, email, userName)).toBe(key(expected));
    });
  });
});
