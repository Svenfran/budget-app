/**
 * Optionen für Ladefunktionen, die der stille Resync nach Reconnect/App-Resume nutzt (DIV-36).
 * Ohne Optionen verhalten sich die Ladefunktionen wie bisher.
 */
export interface LoadOptions {
  /** Kein Leeren und kein Platzhalter bei Fehlern (alter Stand bleibt, nur `console.warn`). */
  silent?: boolean;
  /** Wird beim Eintreffen der Antwort geprüft; `false` = überholt (Nutzer oder Gruppe gewechselt), Antwort verwerfen. */
  isCurrent?: () => boolean;
}
