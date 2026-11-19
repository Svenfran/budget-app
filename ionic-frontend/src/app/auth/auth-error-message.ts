const ERROR_MESSAGES = 'alerts.authenticate.error_messages';

// Backend-Meldung bei Login/Registrierung ohne DeviceId (DIV-46). Fehlermeldungs-Vertrag X3,
// Liste in spring-backend CartControllerApiSpec – Text nur gemeinsam mit dem Backend ändern.
export const DEVICE_ID_REQUIRED = 'DeviceId is required';

/**
 * i18n-Key der Fehlermeldung nach fehlgeschlagenem Login bzw. fehlgeschlagener Registrierung (Status 0 behandelt
 * die Auth-Seite selbst). Die Substring-Prüfungen auf E-Mail und Namen (Vertrag M5/M6) gelten nur für die
 * Registrierung und nur für nicht-leere Werte – beim Login ist der Name leer, und includes('') trifft immer zu.
 */
export function authErrorMessageKey(error: unknown, status: number, isRegistration: boolean,
                                    email: string, userName: string): string {
  // Login-Rate-Limit (DIV-64): Erkennung am Status, nicht am Text – deshalb vor der typeof-Prüfung
  if (status === 429) {
    return `${ERROR_MESSAGES}.message_too_many_attempts`;
  }
  // Falsche Zugangsdaten beim Login (403, leerer Body) – nur diese Versuche zählen, daher hier der Hinweis auf die
  // vorübergehende Sperre (DIV-64); ohne Zahl, weil die Zähler serverseitig je Account/IP geführt werden
  if (!isRegistration && status === 403) {
    return `${ERROR_MESSAGES}.message_invalid_credentials`;
  }
  if (typeof error !== 'string') {
    return `${ERROR_MESSAGES}.message`;
  }
  if (error === DEVICE_ID_REQUIRED) {
    return `${ERROR_MESSAGES}.message_device_id_missing`;
  }
  if (isRegistration && status !== 403) {
    if (email && error.includes(email)) {
      return `${ERROR_MESSAGES}.message_email_exists`;
    }
    if (userName && error.includes(userName)) {
      return `${ERROR_MESSAGES}.message_username_exists`;
    }
  }
  return `${ERROR_MESSAGES}.message`;
}

/** i18n-Key und Parameter einer Fehlermeldung. */
export interface AuthErrorMessage {
  key: string;
  params?: { minutes: number };
}

/**
 * Wie {@link authErrorMessageKey}, bei 429 (Login-Rate-Limit, DIV-64) zusätzlich mit der Restzeit aus dem
 * Retry-After-Header (vom Backend per CORS freigegeben). Ohne lesbare Restzeit bleibt es bei der allgemeinen
 * 429-Meldung.
 */
export function authErrorMessage(error: unknown, status: number, retryAfter: string | null | undefined,
                                 isRegistration: boolean, email: string, userName: string): AuthErrorMessage {
  const minutes = status === 429 ? retryAfterMinutes(retryAfter) : null;
  if (minutes !== null) {
    return { key: `${ERROR_MESSAGES}.message_too_many_attempts_wait`, params: { minutes } };
  }
  return { key: authErrorMessageKey(error, status, isRegistration, email, userName) };
}

/**
 * Restzeit in ganzen Minuten (aufgerundet, mindestens 1) aus einem Retry-After in Sekunden. Das Backend sendet
 * immer Sekunden; die HTTP-Datumsform und ungültige Werte ergeben null.
 */
export function retryAfterMinutes(retryAfter: string | null | undefined): number | null {
  const value = retryAfter?.trim();
  if (!value || !/^\d+$/.test(value)) {
    return null;
  }
  return Math.max(1, Math.ceil(Number(value) / 60));
}
