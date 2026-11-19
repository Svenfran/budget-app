/**
 * Ergebnis von `GroupService.addMemberToGroup` (DIV-80): bei Fehler der i18n-Key der Meldung, die das Mitglieder-Sheet
 * unter dem E-Mail-Feld anzeigt (statt eines Toasts).
 */
export type AddMemberResult = { ok: true } | { ok: false; errorKey: string };
