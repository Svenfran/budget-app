import { Component, DestroyRef, Input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
    private translate: TranslateService,
    private destroyRef: DestroyRef
  ) {}

  ngOnInit() {
    // Abo endet mit dem Modal; bei Logout kein Request ohne Token (DIV-60)
    this.authService.user.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      if (!user) return;
      this.user = user;
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
    
    // Mitgliederliste und Übersicht ändert der Service erst nach Erfolg (DIV-80)
    if (this.newOwner) {
      this.groupService.changeGroupOwner(this.newOwner);
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
          // Mitgliederliste, Übersicht und ggf. aktive Gruppe ändert der Service erst nach Erfolg (DIV-80)
          this.groupService.removeMemberFromGroup(memberToRemove);
        }
      }]
    }).then(alertEl => alertEl.present());

  }
}
