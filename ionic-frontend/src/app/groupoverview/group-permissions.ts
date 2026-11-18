/*
 * Rechte der Gruppenverwaltung (DIV-80) als pure Funktionen. Gleiche Regeln wie bisher und wie im Backend
 * (VerificationService): Bearbeiten, Löschen, Mitglieder hinzufügen und Admin wechseln nur der Gruppenadmin (Owner).
 * Erkannt wird er an der User-ID, nicht am Namen.
 */

/** Ist der Nutzer Admin der Gruppe? Ohne Nutzer (z. B. während des Logouts) nie. */
export function isGroupAdmin(group: { ownerId: number }, user: { id: number } | null | undefined): boolean {
  return !!user && group.ownerId === user.id;
}
