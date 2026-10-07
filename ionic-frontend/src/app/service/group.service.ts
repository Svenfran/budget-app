import { computed, Injectable, signal, WritableSignal } from '@angular/core';
import { environment } from 'src/environments/environment';
import { HttpClient } from '@angular/common/http';
import { Group } from '../model/group';
import { StorageService } from './storage.service';
import { Init } from '../constants/default-values';
import { Zeitraum } from '../domains/cartlist/new-edit-cart/model/gmh-zeitraum';
import { GroupOverview } from '../groupoverview/model/group-overview';
import { User } from '../auth/user';
import { AuthService } from '../auth/auth.service';
import { NewMemberDto } from '../groupoverview/model/new-member-dto';
import { AlertService } from './alert.service';
import { GroupMembers } from '../groupmembers/model/groupmembers-dto';
import { RemoveMemberDto } from '../groupoverview/model/remove-member';
import { ChangeGroupOwnerDto } from '../groupmembers/model/change-group-owner-dto';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class GroupService {
  private apiBaseUrl = environment.apiBaseUrl;
  private groupsSideNavUrl = `${this.apiBaseUrl}/api/groups/sidenav`;
  private addGroupUrl = `${this.apiBaseUrl}/api/groups/add`;
  private gmhUrl = `${this.apiBaseUrl}/api/groups/history-by-group-and-user`;
  private groupsOverviewUrl = `${this.apiBaseUrl}/api/groups/overview`;
  private updateGroupUrl = `${this.apiBaseUrl}/api/groups/update`;
  private deleteGroupUrl = `${this.apiBaseUrl}/api/groups/delete`;
  private groupMembersUrl = `${this.apiBaseUrl}/api/groups/members`;
  private addNewMemberUrl = `${this.apiBaseUrl}/api/groups/add-new-member`;
  private removeMemberUrl = `${this.apiBaseUrl}/api/groups/remove-member-from-group`;
  private changeGroupOwnerUrl = `${this.apiBaseUrl}/api/groups/change-groupowner`;

  public groupsSideNav: WritableSignal<Group[]> = signal<Group[]>([]);
  public groupOverviewList: WritableSignal<GroupOverview[]> = signal<GroupOverview[]>([]);
  public activeGroup: WritableSignal<Group> = signal<Group>(Init.DEFAULT_GROUP)
  public groupMembershipHistory: WritableSignal<Zeitraum[]> = signal<Zeitraum[]>([]);
  public groupMembers: WritableSignal<GroupMembers> = signal<GroupMembers>(Init.DEFAULT_GROUP_MEMBERS);
  public groupMembersWithOwner = computed(() => [{id: this.groupMembers().ownerId, userName: this.groupMembers().ownerName }, ...this.groupMembers().members]);
  public memberUpdated = signal(0);
  public hasNoGroups = signal<boolean>(false);

  private user: User | undefined;

  constructor(
    private http: HttpClient,
    private storageService: StorageService,
    private authService: AuthService,
    private alertService: AlertService,
    private router: Router,
    private translate: TranslateService
  ) {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
    })
  }

  triggerUpdate() {
    this.memberUpdated.set(this.memberUpdated() + 1);
  }

  public setActiveGroup(group: Group) {
    const activeGroup: Group = {
      id: group.id,
      name: group.name,
      dateCreated: group.dateCreated,
      flag: group.flag
    }
    this.storageService.setItem("ACTIVE_GROUP", activeGroup);
    this.activeGroup.set(activeGroup);
  }

 updateActiveGroup(groups: Group[], activeGroup?: Group): void {
    const hasGroups = groups.length > 0;
    const foundGroup = groups.find(gr => activeGroup && gr.id === activeGroup.id);
  
    if (!activeGroup) {
      this.setActiveGroup(hasGroups ? groups[0] : Init.DEFAULT_GROUP);
      return;
    }
  
    if (foundGroup) {
      // Falls sich der Gruppenname geändert hat, setze den aktualisierten Namen
      if (activeGroup.name !== foundGroup.name) {
        this.setActiveGroup(foundGroup);
      } else {
        this.setActiveGroup(activeGroup);
      }
    } else {
      this.setActiveGroup(hasGroups ? groups[0] : Init.DEFAULT_GROUP);
    }
  }

  getGroupsForSideNav(): void {
    this.http
      .get<Group[]>(this.groupsSideNavUrl)
      .subscribe({
        next: async (result) => {
          this.groupsSideNav.set(result || []);

          const activeGroup: Group = await this.storageService.getItem('ACTIVE_GROUP') as {id: number, name: string, dateCreated: Date, flag?: string};
          const groups = this.groupsSideNav();

          this.updateActiveGroup(groups, activeGroup);

          if (groups.length === 0) {
            this.router.navigateByUrl('/no-group', { replaceUrl: true });
            this.hasNoGroups.set(true);
          }
        },
        error: (err) => {
          console.error("Error fetching groups:", err);
          this.groupsSideNav.set([]);
        }
      });
  }


  getGroupsForOverview(): void {
    this.http
      .get<GroupOverview[]>(this.groupsOverviewUrl)
      .subscribe({
        next: async (result) => {
          this.groupOverviewList.set(result || []);
        },
        error: (err) => {
          console.error("Error fetching groups:", err);
          this.groupOverviewList.set([]);
        }
      })
  }

  addGroup(group: Group, callingPage?: string): void {
    const currentGroupsSidenav = this.groupsSideNav();
    const currentGroupsOverview = this.groupOverviewList();
    this.http
      .post<Group>(this.addGroupUrl, group)
      .subscribe({
        next: (result) => {
          // groups for sidenav
          const newGroupSidenav: Group = {
            id: result.id,
            name: result.name,
            dateCreated: result.dateCreated
          }
          this.groupsSideNav.update(groups => [...groups, newGroupSidenav]);
          this.activeGroup.set(newGroupSidenav);

          //groups for overview
          const newGroupOverview: GroupOverview = {
            id: result.id,
            name: result.name,
            dateCreated: result.dateCreated,
            ownerName: this.user?.name!,
            memberCount: 0
          }
          this.groupOverviewList.update(groups => [...groups, newGroupOverview]);

          // navigate to overview page if called from no-group page
          if (callingPage?.includes("no-group")) {
            this.hasNoGroups.set(false);
            this.router.navigateByUrl('/domains/tabs/overview', { replaceUrl: true });
          };
        },
        error: (err) => {
          console.error("Error adding group:", err);
          this.groupsSideNav.set(currentGroupsSidenav);
          this.groupOverviewList.set(currentGroupsOverview);
        }
      })
  }

  updateGroup(group: Group): void {
    const currentGroupsSidenav = this.groupsSideNav();
    const currentGroupsOverview = this.groupOverviewList();

    this.http
      .put<Group>(this.updateGroupUrl, group)
      .subscribe({
        next: (result) => {
          this.groupsSideNav.update(groups => groups.map(gr =>
            gr.id === result.id ? {...gr, name: result.name} : gr
          ));
          this.groupOverviewList.update(groups => groups.map(gr =>
            gr.id === result.id ? {...gr, name: result.name} : gr
          ));
          
          // aktive Gruppe muss nochmal gesetzt werden, damit Überschriften der einzelnen Views aktualisiert wird.
          if (this.activeGroup().id === result.id)  this.setActiveGroup(result);
        },
        error: (err) => {
          console.error("Error updating group:", err);
          this.groupsSideNav.set(currentGroupsSidenav);
          this.groupOverviewList.set(currentGroupsOverview);
        }
      })
  }

  deleteGroup(group: Group): void {
    const currentGroupsSidenav = this.groupsSideNav();
    const currentGroupsOverview = this.groupOverviewList();

    this.http
      .delete<void>(`${this.deleteGroupUrl}/${group.id}`)
      .subscribe({
        next: () => {
          this.groupsSideNav.update(groups => groups.filter(gr => 
            gr.id !== group.id
          ));
          this.groupOverviewList.update(groups => groups.filter(gr => 
            gr.id !== group.id
          ));

          const groups = this.groupsSideNav();
          const hasGroups = groups.length > 0;

          if (this.activeGroup().id === group.id) {
            this.setActiveGroup(hasGroups ? groups[0] : Init.DEFAULT_GROUP);
          };

        },
        error: (err) => {
          console.error("Error deleting group:", err);
          this.groupsSideNav.set(currentGroupsSidenav);
          this.groupOverviewList.set(currentGroupsOverview);
        }
      })
  }

  getGroupMembershipHistoryForGroupAndUser(group: Group): void {
    this.http
      .get<Zeitraum[]>(`${this.gmhUrl}/${group.id}`)
      .subscribe({
        next: (result) => {
          this.groupMembershipHistory.set(result || []);
        },
        error: (err) => {
          console.error("Error fetching group membership history:", err);
          this.groupMembershipHistory.set([]);
        }
      })
  }

  addMemberToGroup(newMemberDto: NewMemberDto): void {
    const currentGroupsOverview = this.groupOverviewList();

    this.http
      .post<any>(this.addNewMemberUrl, newMemberDto)
      .subscribe({
        next: (result) => {
          this.groupOverviewList.update(groups => groups.map(gr => 
            gr.id === result.id ? {...gr, memberCount: result.members.length} : gr
          ));

          const message = this.translate.instant("alerts.group.add_member.error_message_service.message_success");
          this.alertService.showToast(message);
          this.triggerUpdate();
        },
        error: (err) => {
          console.error("Error adding member to group:", err);
          this.groupOverviewList.set(currentGroupsOverview);
          let message = "";

          if (err.error.includes(newMemberDto.newMemberEmail)) {
            message = this.translate.instant("alerts.group.add_member.error_message_service.message_no_exists");
          } else if (err.error.includes("New member equals group owner")) {
            message = this.translate.instant("alerts.group.add_member.error_message_service.message_admin_is_member");
          } else if (err.error.includes("Member already exists")) {
            message = this.translate.instant("alerts.group.add_member.error_message_service.message_already_member");
          } else {
            message = this.translate.instant("alerts.group.add_member.error_message_service.message_default");
          }
          this.alertService.showToast(message);
        }
      })
  }

  getGroupMembers(groupId: number): void {
    this.http
      .get<GroupMembers>(`${this.groupMembersUrl}/${groupId}`)
      .subscribe({
        next: (result) => {
          this.groupMembers.set(result);
        },
        error: (err) => {
          console.error("Error fetching group members:", err);
          this.groupMembers.set(Init.DEFAULT_GROUP_MEMBERS);
        }
      })
  }

  removeMemberFromGroup(removeMemberDto: RemoveMemberDto): void {
    const currentGroupMembers = this.groupMembers();

    this.http
      .post<any>(this.removeMemberUrl, removeMemberDto)
      .subscribe({
        next: (result) => {
          this.groupOverviewList.update(groups => groups.map(gr =>
            gr.id === result.id ? {...gr, memberCount: result.members.length} : gr
          ));
          this.triggerUpdate();

          if (this.groupOverviewList().length === 0) {
            this.router.navigateByUrl('/no-group', { replaceUrl: true });
          }
        },
        error: (err) => {
          console.error("Error removing member from group:", err);
          this.groupMembers.set(currentGroupMembers);
        }
      })
  }

  changeGroupOwner(newOwner: ChangeGroupOwnerDto): void {
    this.http
      .post<void>(this.changeGroupOwnerUrl, newOwner)
      .subscribe({
        next: () => {
          console.log("Group owner successfully changed");
        },
        error: (err) => {
          console.error("Error changing group owner:", err);
        }
      })
  }
}
