import { API_ERROR_CODES, apiErrorCode } from '../service/api-error';

const ERROR_MESSAGES = 'alerts.authenticate.error_messages';

/**
 * i18n-Key der Fehlermeldung nach fehlgeschlagenem Login bzw. fehlgeschlagener Registrierung (Status 0 behandelt die
 * Auth-Seite selbst). Ausgewertet wird nur der Fehlercode des Backends (DIV-108), nie der Text; „E-Mail bzw. Name
 * vergeben“ gilt nur für die Registrierung. Ohne bekannten Code: allgemeine Meldung.
 */
export function authErrorMessageKey(err: unknown, isRegistration: boolean): string {
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
