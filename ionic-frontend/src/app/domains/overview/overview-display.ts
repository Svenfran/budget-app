// Darstellungslogik der Übersicht (DIV-67): reine Funktionen, ohne Angular-Abhängigkeiten testbar.

/** Marker im userName, den das Backend für gelöschte Nutzer liefert. */
export const DELETED = 'DELETED';
/** Marker im userName ("<Name> REMOVED"), den das Backend für aus der Gruppe entfernte Mitglieder liefert. */
export const REMOVED = 'REMOVED';

/** Gelöschter oder entfernter Nutzer: wird grau und kursiv dargestellt. */
export function isInactiveMember(userName: string): boolean {
  return userName.includes(DELETED) || userName.includes(REMOVED);
}

/** Gelöschter Nutzer: bekommt ein Personen-Icon statt Initialen. */
export function isDeletedMarker(userName: string): boolean {
  return userName.includes(DELETED);
}

/**
 * Anzeigename ohne Längenkürzung (gekürzt bzw. umgebrochen wird per CSS):
 * gelöscht → übersetztes Label, entfernt → erstes Wort, sonst unverändert.
 */
export function userDisplayName(userName: string, deletedLabel: string): string {
  if (userName.includes(DELETED)) {
    return deletedLabel;
  }
  if (userName.includes(REMOVED)) {
    return userName.split(' ')[0];
  }
  return userName;
}

/**
 * Initialen für den Avatar: ein Wort → die ersten zwei Zeichen ("Sven" → "SV"),
 * mehrere Wörter → Anfangsbuchstaben der ersten beiden Wörter ("Anna Lena" → "AL").
 */
export function userInitials(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(word => word.length > 0);
  if (words.length === 0) {
    return '';
  }
  const initials = words.length === 1
    ? Array.from(words[0]).slice(0, 2).join('')
    : Array.from(words[0])[0] + Array.from(words[1])[0];
  return initials.toLocaleUpperCase();
}
