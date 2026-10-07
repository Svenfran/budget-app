import { Component, Input, OnInit } from '@angular/core';
import { GroupService } from '../service/group.service';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user';
import { AlertController, ModalController } from '@ionic/angular';
import { FormBuilder, FormGroup } from '@angular/forms';
import { UserDto } from '../model/user-dto';
import { ChangeGroupOwnerDto } from './model/change-group-owner-dto';
import { GroupMembers } from './model/groupmembers-dto';
import { RemoveMemberDto } from '../groupoverview/model/remove-member';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-groupmembers',
  templateUrl: './groupmembers.page.html',
  styleUrls: ['./groupmembers.page.scss'],
  standalone: false
})
export class GroupmembersPage implements OnInit {
  @Input() groupId!: number;
  @Input() groupOwnerName!: string;

  public groupMembers = this.groupService.groupMembers;
  public activeGroup = this.groupService.activeGroup;
  public groupsSideNav = this.groupService.groupsSideNav;
  public groupOverviewList = this.groupService.groupOverviewList;
  public user!: User;
  public isSelected: boolean = false;
  public isNotVisible: boolean = true;
  public form!: FormGroup;
  public memberIndex!: number;
  public newOwner: ChangeGroupOwnerDto | null = null;

  constructor(
    private groupService: GroupService,
    private authService: AuthService,
    private modalCtrl: ModalController,
    private fb: FormBuilder,
    private alertCtrl: AlertController,
    private translate: TranslateService
  ) {}

  ngOnInit() {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
      this.groupService.getGroupMembers(this.groupId);
    });

    this.form = this.fb.group({
      selectedOption: [null]
    })
  }

  get selectedOption() {return this.form.get('selectedOption');}

  onDismiss() {
    if (!this.isSelected) {
      this.modalCtrl.dismiss();
      return;
    }
    
    if (this.newOwner) {
      this.groupService.changeGroupOwner(this.newOwner);

      this.groupMembers.update(group => {
        // Den aktuellen Owner speichern, bevor er ersetzt wird
        const previousOwner: UserDto = { id: group.ownerId, userName: group.ownerName };
      
        return {
          ...group,
          ownerName: this.newOwner!.newOwner.userName,
          ownerId: this.newOwner!.newOwner.id,
          members: [
            ...group.members.filter(member => member.id !== this.newOwner!.newOwner.id),
            previousOwner
          ]
        };
      });
      
      this.groupOverviewList.update(groups => {
        return groups.map(group =>
          group.id === this.newOwner?.groupId 
            ? { ...group, ownerName: this.newOwner?.newOwner.userName }
            : group
        );
      });
      
    }
    
    this.modalCtrl.dismiss();
  }

  toggleVisability() {
    this.isNotVisible = !this.isNotVisible;
    if (this.isNotVisible) {
      this.isSelected = false;
      this.form.reset();
    }
  }

  getSelectedMember(event: any, member: UserDto, groupId: number, index: number) {
    this.memberIndex = index;
    this.isSelected = !event.target.attributes['class'].value.includes('checked');
    
    if (this.isSelected) {
      const changeOwner: ChangeGroupOwnerDto = {
        newOwner: { id: member.id, userName: member.userName },
        groupId: groupId
      };
      this.newOwner = changeOwner;
    } else {
      this.newOwner = null;
    }
  }

  removeMemberFromGroup(member: UserDto, groupWithMembers: GroupMembers) {
    const memberToRemove: RemoveMemberDto = {
      id: groupWithMembers.id,
      name: groupWithMembers.name,
      member: member
    };

    this.alertCtrl.create({
      header: this.translate.instant("alerts.group.remove_member.header"),
      message: this.translate.instant("alerts.group.remove_member.message", { memberName: member.userName, group: groupWithMembers.name }),
      buttons: [{
        text: this.translate.instant("alerts.group.remove_member.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.remove_member.ok"),
        handler: () => {
          this.groupService.removeMemberFromGroup(memberToRemove);
          this.groupMembers.update(group => {
            return { ...group, members: group.members.filter(m => m.id !== member.id) };
          });

          if (this.user?.id === member.id) {
            this.groupOverviewList.update(groups => {
              return groups.filter(g => g.id !== groupWithMembers.id);  
            });
            this.groupsSideNav.update(groups => {
              return groups.filter(g => g.id !== groupWithMembers.id);
            });
            this.groupService.updateActiveGroup(this.groupsSideNav(), this.activeGroup());
          };
        } 
      }]
    }).then(alertEl => alertEl.present());

  }
}
