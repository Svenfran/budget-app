import { ChangeGroupOwnerDto } from '../groupmembers/model/change-group-owner-dto';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { GroupOverview } from '../groupoverview/model/group-overview';
import { UserDto } from '../model/user-dto';

/*
 * Zustandsänderungen der Gruppen durch WebSocket-Events (DIV-36), als pure Funktionen.
 * Unbekannte Gruppen ändern nichts und werfen nicht.
 */

/** Übernimmt den neuen Namen für die Gruppe mit `data.id` (Seitenleiste oder Übersicht). */
export function renameGroup<T extends { id: number; name: string }>(groups: T[], data: { id: number; name: string }): T[] {
  return groups.map(group => group.id === data.id ? { ...group, name: data.name } : group);
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
  updatedMember: UserDto;
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
