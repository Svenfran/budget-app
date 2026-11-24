import { ChangeGroupOwnerDto } from '../groupmembers/model/change-group-owner-dto';
import { User } from '../auth/user';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { GroupOverview } from '../groupoverview/model/group-overview';
import { UserDto } from '../model/user-dto';

/*
 * Zustandsänderungen der Gruppen durch WebSocket-Events (DIV-36), als pure Funktionen.
 * Unbekannte Gruppen ändern nichts und werfen nicht.
 */

/** Payload von `update-group` (Backend `GroupDto`); `currencyCode` fehlt nur bei einem Backend vor DIV-77. */
export interface GroupUpdateEvent {
  id: number;
  name: string;
  currencyCode?: string;
}

/**
 * Übernimmt Name und Währung für die Gruppe mit `data.id` (Seitenleiste oder Übersicht; DIV-77). Fehlt `currencyCode`
 * im Payload, bleibt die bisherige Währung. Eine noch unbekannte Gruppe ändert nichts – deshalb lädt der Nutzer nach
 * der eigenen Aufnahme still neu (`isOwnMembershipEvent`, D8).
 */
export function applyGroupUpdate<T extends { id: number; name: string; currencyCode?: string }>(groups: T[], data: GroupUpdateEvent): T[] {
  return groups.map(group => group.id === data.id
    ? { ...group, name: data.name, ...(data.currencyCode ? { currencyCode: data.currencyCode } : {}) }
    : group);
}

/**
 * Neuer Mitgliederstand einer Gruppe (Antwort von Hinzufügen/Entfernen bzw. dessen WebSocket-Event, DIV-80) in der
 * geladenen Mitgliederliste – nur, wenn sie zur betroffenen Gruppe gehört. Übernimmt den vollständigen Stand (kein
 * Delta), ein doppeltes Event ändert daher nichts mehr.
 */
export function applyMembersChange(groupMembers: GroupMembers, data: GroupMembers): GroupMembers {
  if (groupMembers.id !== data.id) return groupMembers;
  return { ...groupMembers, ownerName: data.ownerName, ownerId: data.ownerId, members: data.members };
}

/** Payload von `add-group-member`/`remove-group-member` (Backend `GroupMembersDto` mit `updatedMember`). */
export interface GroupMembersEvent extends GroupMembers {
  dateCreated: Date;
  /** Gruppenwährung (DIV-77, aus `GroupSideNavDto`). */
  currencyCode: string;
  updatedMember: UserDto;
}

/**
 * Betrifft ein `add-group-member`-Event den angemeldeten Nutzer selbst (DIV-77, D8)? Dann lädt die App Seitenleiste und
 * Übersicht still neu: Das Payload kann eine veraltete Währung tragen, und ein `update-group` kann vor der Aufnahme
 * eingetroffen und mangels bekannter Gruppe ignoriert worden sein.
 */
export function isOwnMembershipEvent(data: Pick<GroupMembersEvent, 'updatedMember'>, user: Pick<User, 'id'> | undefined): boolean {
  return !!user && data.updatedMember?.id === user.id;
}

/**
 * Mitglied hinzugefügt (Event, DIV-80): Wurde der Nutzer selbst aufgenommen, kommt die Gruppe in die Übersicht
 * (nicht doppelt, falls sie schon da ist); sonst ändert sich nur die Mitgliederzahl.
 */
export function applyMemberAddedToOverview(groups: GroupOverview[], data: GroupMembersEvent, userId?: number): GroupOverview[] {
  if (data.updatedMember.id === userId && !groups.some(group => group.id === data.id)) {
    return [...groups, {
      id: data.id,
      name: data.name,
      dateCreated: data.dateCreated,
      ownerName: data.ownerName,
      ownerId: data.ownerId,
      memberCount: data.members.length,
      currencyCode: data.currencyCode,
      // hasBookings bleibt unbekannt (undefined): das Sheet fragt beim Währungswechsel vorsorglich nach
    }];
  }
  return withMemberCount(groups, data);
}

/** Mitglied entfernt (Event, DIV-80): eigene Entfernung nimmt die Gruppe heraus, sonst nur die Mitgliederzahl. */
export function applyMemberRemovedFromOverview(groups: GroupOverview[], data: GroupMembersEvent, userId?: number): GroupOverview[] {
  if (data.updatedMember.id === userId) return groups.filter(group => group.id !== data.id);
  return withMemberCount(groups, data);
}

/** Owner-Wechsel in der Übersicht: Name und ID des neuen Admins (DIV-80). */
export function applyOwnerChangeToOverview(groups: GroupOverview[], data: ChangeGroupOwnerDto): GroupOverview[] {
  return groups.map(group => group.id === data.groupId
    ? { ...group, ownerName: data.newOwner.userName, ownerId: data.newOwner.id }
    : group);
}

function withMemberCount(groups: GroupOverview[], data: GroupMembers): GroupOverview[] {
  return groups.map(group => group.id === data.id ? { ...group, memberCount: data.members.length } : group);
}

/**
 * Owner-Wechsel in der geladenen Mitgliederliste – nur, wenn sie zur betroffenen Gruppe gehört. Der neue Owner verlässt
 * die Mitglieder, der bisherige wird mit seiner User-ID (`ownerId`) Mitglied. Ist der neue Owner bereits Owner (Resync
 * lieferte schon den neuen Stand oder das Event kommt doppelt), bleibt die Liste unverändert.
 */
export function applyOwnerChange(groupMembers: GroupMembers, data: ChangeGroupOwnerDto): GroupMembers {
  if (groupMembers.id !== data.groupId || groupMembers.ownerId === data.newOwner.id) return groupMembers;
  const previousOwner: UserDto = { id: groupMembers.ownerId, userName: groupMembers.ownerName };
  return {
    ...groupMembers,
    ownerName: data.newOwner.userName,
    ownerId: data.newOwner.id,
    members: [...groupMembers.members.filter(member => member.id !== data.newOwner.id), previousOwner],
  };
}
