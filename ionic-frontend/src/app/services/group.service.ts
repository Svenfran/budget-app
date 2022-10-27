import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject} from 'rxjs';
import { environment } from 'src/environments/environment';
import { Group } from '../models/group';
import { GroupMembers } from '../models/group-members';
import { GroupOverview } from '../models/group-overview';
import { GroupSideNav } from '../models/group-side-nav';
import { NewMemberDto } from '../models/new-member-dto';
import { RemoveMemberDto } from '../models/remove-member-dto';

@Injectable({
  providedIn: 'root'
})
export class GroupService {

  private _activeGroup = new BehaviorSubject<GroupSideNav>(null);
  private _groupModified = new BehaviorSubject<boolean>(false);

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private groupsSideNavUrl = `${this.apiBaseUrl}/api/groups/sidenav`;
  private groupsOverviewUrl = `${this.apiBaseUrl}/api/groups/overview`;
  private groupMembersUrl = `${this.apiBaseUrl}/api/groups/members`;
  private addGroupUrl = `${this.apiBaseUrl}/api/groups/add`;
  private updateGroupUrl = `${this.apiBaseUrl}/api/groups/update`;
  private deleteGroupUrl = `${this.apiBaseUrl}/api/groups/delete`;
  private addNewMemberUrl = `${this.apiBaseUrl}/api/groups/add-new-member`;
  private removeMemberUrl = `${this.apiBaseUrl}/api/groups/remove-member-from-group`;

  constructor(private http: HttpClient) { }

  setActiveGroup(activeGroup: GroupSideNav) {
    this._activeGroup.next(activeGroup);
  }

  setGroupModified(groupAdded: boolean) {
    this._groupModified.next(groupAdded);
  }

  get activeGroup() {
    return this._activeGroup.asObservable();
  }

  get groupModified() {
    return this._groupModified.asObservable();
  }

  getGroupsForSideNav(): Observable<GroupSideNav[]> {
    return this.http.get<GroupSideNav[]>(this.groupsSideNavUrl);
  }
  
  getGroupsForOverview(): Observable<GroupOverview[]> {
    return this.http.get<GroupOverview[]>(this.groupsOverviewUrl);
  }
  
  getGroupMembers(groupId: number): Observable<GroupMembers> {
    return this.http.get<GroupMembers>(`${this.groupMembersUrl}/${groupId}`);
  }

  addGroup(group: Group): Observable<Group> {
    return this.http.post<Group>(this.addGroupUrl, group);
  }

  updateGroup(group: Group): Observable<Group> {
    return this.http.put<Group>(this.updateGroupUrl, group);
  }

  deleteGroup(groupId: number): Observable<void> {
    const deleteGroupUrl = `${this.deleteGroupUrl}/${groupId}`;
    return this.http.delete<void>(deleteGroupUrl);
  }

  addMemberToGroup(newMemberDto: NewMemberDto): Observable<NewMemberDto> {
    return this.http.put<NewMemberDto>(this.addNewMemberUrl, newMemberDto);
  }

  removeMemberFromGroup(removeMemberDto: RemoveMemberDto): Observable<RemoveMemberDto> {
    return this.http.put<RemoveMemberDto>(this.removeMemberUrl, removeMemberDto);
  }
}
