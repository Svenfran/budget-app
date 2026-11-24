import { API_ERROR_CODES, apiErrorCode } from '../service/api-error';

const ERROR_MESSAGES = 'alerts.authenticate.error_messages';

/**
 * i18n-Key der Fehlermeldung nach fehlgeschlagenem Login bzw. fehlgeschlagener Registrierung (Status 0 behandelt die
 * Auth-Seite selbst). Ausgewertet wird nur der Fehlercode des Backends (DIV-108), nie der Text; „E-Mail bzw. Name
 * vergeben“ gilt nur für die Registrierung. Ohne bekannten Code: allgemeine Meldung.
 * Login-Rate-Limit (DIV-64): 429 und falsche Zugangsdaten (403 ohne Code) werden am Status erkannt.
 */
export function authErrorMessageKey(err: unknown, isRegistration: boolean): string {
  const status = errorStatus(err);
  // Rate-Limit am Status, nicht am Code – so auch bei Antworten ohne JSON-Body (älteres Backend, Proxy)
  if (status === 429) {
    return `${ERROR_MESSAGES}.message_too_many_attempts`;
  }
  // Falsche Zugangsdaten beim Login (403, leerer Body) – nur diese Versuche zählen, daher hier der Hinweis auf die
  // vorübergehende Sperre; ohne Zahl, weil die Zähler serverseitig je Account/IP geführt werden
  if (!isRegistration && status === 403) {
    return `${ERROR_MESSAGES}.message_invalid_credentials`;
  }
  const code = apiErrorCode(err);
  if (code === API_ERROR_CODES.DEVICE_ID_REQUIRED) {
    return `${ERROR_MESSAGES}.message_device_id_missing`;
  }
  if (isRegistration && code === API_ERROR_CODES.EMAIL_ALREADY_EXISTS) {
    return `${ERROR_MESSAGES}.message_email_exists`;
  }
  if (isRegistration && code === API_ERROR_CODES.USER_NAME_ALREADY_EXISTS) {
    return `${ERROR_MESSAGES}.message_username_exists`;
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
export function authErrorMessage(err: unknown, retryAfter: string | null | undefined,
                                 isRegistration: boolean): AuthErrorMessage {
  const minutes = errorStatus(err) === 429 ? retryAfterMinutes(retryAfter) : null;
  if (minutes !== null) {
    return { key: `${ERROR_MESSAGES}.message_too_many_attempts_wait`, params: { minutes } };
  }
  return { key: authErrorMessageKey(err, isRegistration) };
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

/** HTTP-Status einer Fehlerantwort (`HttpErrorResponse` o. ä.), sonst `null`. */
function errorStatus(err: unknown): number | null {
  const status = typeof err === 'object' && err !== null ? (err as { status?: unknown }).status : undefined;
  return typeof status === 'number' ? status : null;
}
