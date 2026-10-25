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
