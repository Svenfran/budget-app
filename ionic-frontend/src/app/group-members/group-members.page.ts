import { Component, Input, OnInit } from '@angular/core';
import { AlertController, ModalController } from '@ionic/angular';
import { ChangeGroupOwner } from '../models/change-group-owner';
import { Group } from '../models/group';
import { GroupMembers } from '../models/group-members';
import { GroupSideNav } from '../models/group-side-nav';
import { RemoveMemberDto } from '../models/remove-member-dto';
import { UserDto } from '../models/user';
import { CartService } from '../services/cart.service';
import { GroupService } from '../services/group.service';
import { SpendingsOverviewService } from '../services/spendings-overview.service';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-group-members',
  templateUrl: './group-members.page.html',
  styleUrls: ['./group-members.page.scss'],
})
export class GroupMembersPage implements OnInit {
  @Input() groupId: number;
  @Input() groupOwnerName: string;

  groupWithMembers: GroupMembers;
  userName: string;
  currentUser: UserDto;
  groupMembers: UserDto[] = [];
  groupsSideNav: GroupSideNav[] = [];
  activeGroup: Group;
  changeOwner: ChangeGroupOwner;
  isSelected: boolean;
  isNotVisible: boolean = true;
  memberIndex: number;

  constructor(
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private modalCtrl: ModalController,
    private cartService: CartService,
    private spendingsService: SpendingsOverviewService,
    private storageService: StorageService,
    private authService: AuthService) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getGroupMembers();
  }

  getGroupMembers() {
    this.groupService.getGroupMembers(this.groupId).subscribe(group => {
      this.groupWithMembers = group;
      this.groupMembers = group.members.sort((a, b) => a.userName < b.userName ? -1 : 1);
    })
  }

  getGroupsForSideNav() {
    this.groupService.getGroupsForSideNav().subscribe(groups => {
      this.groupsSideNav = groups;
    })
  }
  
  getActiveGroup() {
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroup = group;
    })
  }

  onDelete(member: UserDto, groupWithMembers: GroupMembers) {
    let memberToRemove = new UserDto(
      member.id,
      member.userName
    )
    let removeGroupMember = new RemoveMemberDto(
      groupWithMembers.id,
      groupWithMembers.name,
      memberToRemove
    )

    this.getGroupsForSideNav();
    this.getActiveGroup();

    this.alertCtrl.create({
      header: "Löschen",
      message: `Möchtest du den Nutzer "${this.toTitleCase(member.userName)}" 
                wirklich aus der Gruppe "${groupWithMembers.name}" entfernen 
                inkl. aller gespeicherten Ausgaben?`,
      buttons: [{
        text: "Nein",
        role: "cancel"
      }, {
        text: "Ja",
        handler: () => {
          if (this.groupsSideNav.length > 0 && (memberToRemove.id == this.currentUser.id) && this.activeGroup.id == groupWithMembers.id) {
            const newGroup = new Group(
              this.groupsSideNav[0].id,
              this.groupsSideNav[0].name,
              this.groupsSideNav[0].dateCreated
            );
            this.groupService.setActiveGroup(newGroup);
            this.storageService.setActiveGroup(newGroup);
          }

          this.groupService.removeMemberFromGroup(removeGroupMember).subscribe(() => {
            this.groupMembers = this.groupWithMembers.members.filter(m => m.id !== member.id);
            this.groupWithMembers = new GroupMembers(
              groupWithMembers.id, 
              groupWithMembers.name,
              groupWithMembers.ownerName,
              groupWithMembers.ownerId, 
              this.groupMembers
              );
          })

          if (this.groupsSideNav.length === 1 && memberToRemove.id === this.currentUser.id) {
            this.groupService.setActiveGroup(null);
            this.groupService.activeGroup.subscribe(actGroup => {
              this.storageService.setActiveGroup(actGroup)
            })
          }

        }
      }]
    }).then(alertEl => alertEl.present());
  }

  onDismiss() {
    this.modalCtrl.dismiss(this.groupWithMembers);
    if (this.changeOwner) {
      this.groupService.changeGroupOwner(this.changeOwner).subscribe(() => {
        this.groupService.setGroupModified(true);
      });
    }
    if (this.changeOwner && this.groupsSideNav.length !== 1 && this.changeOwner.newOwner.id !== this.currentUser.id) {
      this.cartService.setCartModified(true);
      this.spendingsService.setSpendingsModified(true);
    }
  }

  getSelectedMember(event: any, member: UserDto, groupId: number, index: number) {
    this.memberIndex = index;
    this.isSelected = !event.target.attributes['class'].value.includes('checked');
    if (this.isSelected) {
      this.changeOwner = new ChangeGroupOwner(
        new UserDto(member.id, member.userName),
        groupId
      )
    } else {
      this.changeOwner = null;
    }
  }

  toggleVisability() {
    this.isNotVisible = !this.isNotVisible;
  }


  private toTitleCase(text: string) {
    return text.charAt(0).toUpperCase() + text.substring(1).toLowerCase();
  }

  getCurrentUser() {
    this.authService.user.subscribe(user => {
      this.userName = user.name;
      this.currentUser = new UserDto(user.id, user.name);
    })
    return this.userName;
  }
}

