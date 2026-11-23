import { authErrorMessageKey } from './auth-error-message';

describe('authErrorMessageKey', () => {
  const key = (name: string) => `alerts.authenticate.error_messages.${name}`;
  const err = (status: number, error: unknown) => ({ status, error });
  const coded = (status: number, code: string, message: string) => err(status, { code, message });

  // Fehlercodes wie in AuthenticationControllerApiSpec (Vertrag M5, M6, X3; DIV-108)
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
    ['Login mit falschem Passwort (403 ohne Code) → allgemeine Meldung',
      err(403, ''), false, 'message'],
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
