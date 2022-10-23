import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject} from 'rxjs';
import { environment } from 'src/environments/environment';
import { Group } from '../models/group';
import { GroupMembers } from '../models/group-members';
import { GroupOverview } from '../models/group-overview';
import { GroupSideNav } from '../models/group-side-nav';

@Injectable({
  providedIn: 'root'
})
export class GroupService {

  private _activeGroup = new BehaviorSubject<GroupSideNav>(null);

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private groupsSideNavUrl = `${this.apiBaseUrl}/api/grouplist-sidenav`;
  private groupsOverviewUrl = `${this.apiBaseUrl}/api/grouplist-groupoverview`;
  private groupMembersUrl = `${this.apiBaseUrl}/api/groups`;
  private addGroupUrl = `${this.apiBaseUrl}/api/group/add`;

  constructor(private http: HttpClient) { }

  setActiveGroup(activeGroup: GroupSideNav) {
    this._activeGroup.next(activeGroup);
  }

  get activeGroup() {
    return this._activeGroup.asObservable();
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
}
