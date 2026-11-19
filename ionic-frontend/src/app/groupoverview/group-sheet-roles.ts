/*
 * Schließ-Rollen der Gruppen-Sheets (DIV-80), gemeinsam für „Gruppe hinzufügen / bearbeiten“ und „Mitglieder verwalten“.
 */

/** Nach erfolgreichem Speichern schließt das Sheet mit dieser Rolle. */
export const GROUP_SHEET_SAVED_ROLE = 'saved';

/**
 * Die Gruppe ist für den Nutzer verschwunden (gelöscht oder er wurde entfernt, z. B. per Live-Event): Das Sheet schließt
 * mit dieser Rolle, und `canDismiss` lässt sie **vor** allen Sperren (laufendes Speichern, Ansicht „Hinzufügen“) durch
 * (Codex-Plan-Review F-002; Ionic prüft `canDismiss` auch beim programmatischen dismiss()).
 */
export const GROUP_GONE_ROLE = 'group-gone';
