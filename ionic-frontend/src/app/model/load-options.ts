/**
 * Optionen für Ladefunktionen, die der stille Resync nach Reconnect/App-Resume nutzt (DIV-36).
 * Ohne Optionen verhalten sich die Ladefunktionen wie bisher; Ausnahme ist die Sitzungsprüfung (siehe `isCurrent`).
 */
export interface LoadOptions {
  /** Kein Leeren und kein Platzhalter bei Fehlern (alter Stand bleibt, nur `console.warn`). */
  silent?: boolean;
  /**
   * Wird beim Eintreffen der Antwort geprüft; `false` = überholt (Nutzer oder Gruppe gewechselt), Antwort verwerfen.
   * Ohne Angabe prüfen `getGroupsForSideNav`/`getGroupsForOverview` die Anmelde-Sitzung beim Start des Requests (DIV-58).
   */
  isCurrent?: () => boolean;
}
