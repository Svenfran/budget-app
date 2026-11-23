/**
 * Maschinenlesbare Fehlercodes des Backends (DIV-108). Der Interceptor fordert mit `X-Error-Format: json` den Fehlerbody
 * `{ code, message, details? }` an; ausgewertet wird nur `code` (und `details`), nie der Text in `message`.
 *
 * Nur die Codes, die das Frontend auswertet. Die vollständige Liste und den Vertrag pinnt das Backend
 * (`ApiErrorCode`, Groovydoc von `CartControllerApiSpec`) – Codes nur gemeinsam ändern.
 */
export const API_ERROR_CODES = {
  DATE_NOT_WITHIN_MEMBERSHIP: 'DATE_NOT_WITHIN_MEMBERSHIP',
  USER_NOT_FOUND: 'USER_NOT_FOUND',
  MEMBER_EQUALS_OWNER: 'MEMBER_EQUALS_OWNER',
  MEMBER_ALREADY_EXISTS: 'MEMBER_ALREADY_EXISTS',
  EMAIL_ALREADY_EXISTS: 'EMAIL_ALREADY_EXISTS',
  USER_NAME_ALREADY_EXISTS: 'USER_NAME_ALREADY_EXISTS',
  USER_NAME_NOT_ALLOWED: 'USER_NAME_NOT_ALLOWED',
  INVALID_EMAIL: 'INVALID_EMAIL',
  INCORRECT_PASSWORD: 'INCORRECT_PASSWORD',
  DEVICE_ID_REQUIRED: 'DEVICE_ID_REQUIRED',
  CURRENCY_CHANGE_CONFIRMATION_REQUIRED: 'CURRENCY_CHANGE_CONFIRMATION_REQUIRED',
} as const;

/** Request-Header, mit dem die App das JSON-Fehlerformat anfordert (Backend: CORS-allowedHeaders). */
export const ERROR_FORMAT_HEADER = 'X-Error-Format';
export const ERROR_FORMAT_JSON = 'json';

/**
 * Fehlercode aus einer Fehlerantwort (`HttpErrorResponse` o. ä.: liest `err.error.code`). `null`, wenn der Body kein
 * Objekt mit String-`code` ist: Text (älteres Backend), `ProgressEvent` (Netzwerk), `null`, Blob, Spring-Standardfehler.
 */
export function apiErrorCode(err: unknown): string | null {
  const code = errorBody(err)?.['code'];
  return typeof code === 'string' ? code : null;
}

/** `details.userId` einer Fehlerantwort (z. B. betroffene Partei bei `DATE_NOT_WITHIN_MEMBERSHIP`), sonst `null`. */
export function apiErrorUserId(err: unknown): number | null {
  const details = errorBody(err)?.['details'];
  const userId = isRecord(details) ? details['userId'] : undefined;
  return typeof userId === 'number' ? userId : null;
}

function errorBody(err: unknown): Record<string, unknown> | null {
  const body = isRecord(err) ? err['error'] : undefined;
  return isRecord(body) ? body : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
