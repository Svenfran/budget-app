import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject} from 'rxjs';
import { environment } from 'src/environments/environment';
import { ChangeGroupOwner } from '../models/change-group-owner';
import { Group } from '../models/group';
import { GroupMembers } from '../models/group-members';
import { GroupOverview } from '../models/group-overview';
import { GroupSideNav } from '../models/group-side-nav';
import { NewMemberDto } from '../models/new-member-dto';
import { RemoveMemberDto } from '../models/remove-member-dto';
import { UserDto } from '../models/user';
import { Zeitraum } from '../models/zeitraum';


@Injectable({
  providedIn: 'root'
})
export class GroupService {

  private _activeGroup = new BehaviorSubject<Group>(null);
  private _groupModified = new BehaviorSubject<boolean>(false);
  // private _currentUser = new BehaviorSubject<UserDto>(null);

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private groupsSideNavUrl = `${this.apiBaseUrl}/api/groups/sidenav`;
  private groupsOverviewUrl = `${this.apiBaseUrl}/api/groups/overview`;
  private groupMembersUrl = `${this.apiBaseUrl}/api/groups/members`;
  private addGroupUrl = `${this.apiBaseUrl}/api/groups/add`;
  private updateGroupUrl = `${this.apiBaseUrl}/api/groups/update`;
  private deleteGroupUrl = `${this.apiBaseUrl}/api/groups/delete`;
  private addNewMemberUrl = `${this.apiBaseUrl}/api/groups/add-new-member`;
  private removeMemberUrl = `${this.apiBaseUrl}/api/groups/remove-member-from-group`;
  private changeGroupOwnerUrl = `${this.apiBaseUrl}/api/groups/change-groupowner`;
  private gmhUrl = `${this.apiBaseUrl}/api/groups/history-by-group-and-user`;

  constructor(private http: HttpClient) { }

  setActiveGroup(activeGroup: Group) {
    let noGroupAvailable = new Group(null, 'Keine Gruppe vorhanden!', null)
    if (!activeGroup) {
      this._activeGroup.next(noGroupAvailable);
    } else {
      this._activeGroup.next(activeGroup);
    }
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

  // get currentUser() {
  //   return this._currentUser.asObservable();
  // }

  getGroupsForSideNav(): Observable<GroupSideNav[]> {
    return this.http.get<GroupSideNav[]>(this.groupsSideNavUrl);
  }

  // private groupListSubject = new BehaviorSubject<GroupSideNav[]>([]);
  // groupListSideNav$ = this.groupListSubject.asObservable();

  // loadGroups() {
  //   this.getGroupsForSideNav().subscribe(groups => {
  //     this.groupListSubject.next(groups); // Die neue Liste in das Subject setzen
  //   });
  // }
  
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
    return this.http.post<NewMemberDto>(this.addNewMemberUrl, newMemberDto);
  }

  removeMemberFromGroup(removeMemberDto: RemoveMemberDto): Observable<RemoveMemberDto> {
    return this.http.post<RemoveMemberDto>(this.removeMemberUrl, removeMemberDto);
  }

  changeGroupOwner(changeGroupOwner: ChangeGroupOwner): Observable<void> {
    return this.http.post<void>(this.changeGroupOwnerUrl, changeGroupOwner);
  }

  getGroupMembershipHistoryForGroupAndUser(groupId: number): Observable<Zeitraum[]> {
    const gmhUrl = `${this.gmhUrl}/${groupId}`;
    return this.http.get<Zeitraum[]>(gmhUrl)
  }
}
