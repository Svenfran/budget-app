/*
 * Rechte der Gruppenverwaltung (DIV-80) als pure Funktionen. Gleiche Regeln wie bisher und wie im Backend
 * (VerificationService): Bearbeiten, Löschen, Mitglieder hinzufügen und Admin wechseln nur der Gruppenadmin (Owner).
 * Erkannt wird er an der User-ID, nicht am Namen.
 */

/** Ist der Nutzer Admin der Gruppe? Ohne Nutzer (z. B. während des Logouts) nie. */
export function isGroupAdmin(group: { ownerId: number }, user: { id: number } | null | undefined): boolean {
  return !!user && group.ownerId === user.id;
}

export interface MemberActions {
  /** „Zum Admin machen“: nur der Admin, für ein anderes Mitglied. */
  makeAdmin: boolean;
  /** „Mitglied entfernen“: der Admin für jedes Mitglied, ein Mitglied nur für sich selbst (Backend: verifyIsOwnerOrMemberToRemove). */
  remove: boolean;
}

/**
 * Aktionen, die der Nutzer im Mitglieder-Sheet für ein Mitglied hat (wie bisher in GroupmembersPage). Die Admin-Zeile
 * selbst hat nie Aktionen: Der Admin wechselt nur über „Zum Admin machen“ bei einem anderen Mitglied.
 */
export function memberActions(group: { ownerId: number }, userId: number | null | undefined, memberId: number): MemberActions {
  if (userId == null || memberId === group.ownerId) return { makeAdmin: false, remove: false };
  const admin = group.ownerId === userId;
  return { makeAdmin: admin, remove: admin || memberId === userId };
}
