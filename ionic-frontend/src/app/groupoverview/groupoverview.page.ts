import { Component, OnInit } from '@angular/core';
import { GroupService } from '../service/group.service';
import { User } from '../auth/user';
import { AuthService } from '../auth/auth.service';
import { AlertController, IonItemSliding, LoadingController, ModalController, Platform } from '@ionic/angular';
import { Group } from '../model/group';
import { NewMemberDto } from './model/new-member-dto';
import { GroupOverview } from './model/group-overview';
import { GroupmembersPage } from '../groupmembers/groupmembers.page';
import { INIT_NUMBERS } from '../constants/default-values';
import { Router } from '@angular/router';
import { EmailValidator } from '../Validator/email-validator';
import { AlertService } from '../service/alert.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-groupoverview',
  templateUrl: './groupoverview.page.html',
  styleUrls: ['./groupoverview.page.scss'],
  standalone: false
})
export class GroupoverviewPage implements OnInit {

  public isLoading: boolean = false;
  public groupOverviewList = this.groupService.groupOverviewList;
  public user: User | undefined;

  constructor(
    private groupService: GroupService,
    private authService: AuthService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private modalCtrl: ModalController,
    private router: Router,
    private alertService: AlertService,
    private translate: TranslateService
  ) {}

  ngOnInit() {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
      this.groupService.getGroupsForOverview();
    })
  }

  refreshGroupList(event: CustomEvent) {
    setTimeout(() => {
      this.groupService.getGroupsForOverview();
      (event.target as HTMLIonRefresherElement).complete();
    }, 2000);
  }

  async onSelectGroup(group: GroupOverview, slidingItem: IonItemSliding) {
    // Ist das Item gerade aufgeswiped, nur schließen statt navigieren
    if (await slidingItem.getOpenAmount() !== 0) {
      slidingItem.close();
      return;
    }
    if (this.groupService.activeGroup().id !== group.id) {
      this.groupService.setActiveGroup(group);
    }
    this.router.navigateByUrl('/domains/tabs/overview', { replaceUrl: true });
  }

  onCreateGroup() {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.group.new.header"),
      buttons: [{
        text: this.translate.instant("alerts.group.new.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.new.ok"),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.group.new.loading")
          }).then(loadingEl => {
            if (!data) return;
            const trimmedGroupName = data.groupName.trim();
            if (trimmedGroupName === "") return;

            const newGroup: Group = { id: new Date().getTime(), name: trimmedGroupName, dateCreated: new Date() };
            this.groupService.addGroup(newGroup);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          placeholder: this.translate.instant("alerts.group.new.placeholder"),
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector("ion-alert input") as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  onUpdateGroup(group: Group, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: this.translate.instant("alerts.group.edit.header"),
      buttons: [{
        text: this.translate.instant("alerts.group.edit.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.edit.ok"),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.group.edit.loading")
          }).then(loadingEl => {
            if (!data) return;
            const trimmedGroupName = data.groupName.trim();
            if (trimmedGroupName === "") return;

            const newGroup: Group = { id: group.id, name: trimmedGroupName, dateCreated: new Date() };
            this.groupService.updateGroup(newGroup);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          value: this.groupOverviewList().find(gr => gr.id === group.id)?.name,
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector("ion-alert input") as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  onDeleteGroup(group: Group, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: this.translate.instant("alerts.group.delete.header"),
      message: this.translate.instant("alerts.group.delete.message", {groupName: group.name}),
      buttons: [{
        text: this.translate.instant("alerts.group.delete.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.delete.ok"),
        handler: () => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.group.delete.loading")
          }).then(loadingEl => {
            this.groupService.deleteGroup(group);
            loadingEl.dismiss();
            // Redirect to no-group page if last group is deleted
            if (this.groupOverviewList().length === 1 && this.groupOverviewList()[0].id === group.id) {
              this.groupService.hasNoGroups.set(true);
              this.router.navigate(['/no-group'], { replaceUrl: true });
            }
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  onAddMember(group: Group, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: this.translate.instant("alerts.group.add_member.header"),
      buttons: [{
        text: this.translate.instant("alerts.group.add_member.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.group.add_member.ok"),
        handler: (data) => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.group.add_member.loading")
          }).then(loadingEl => {
            if (!data) return;
            const trimmedEmail = data.memberEmail.trim();
            if (EmailValidator.isNotValid(trimmedEmail)) {
              let header = this.translate.instant("alerts.group.add_member.error_message_page.header");
              let message = this.translate.instant("alerts.group.add_member.error_message_page.message");
              this.alertService.showErrorAlert(header, message);
              return
            }

            let newMember: NewMemberDto = {
              id: group.id,
              name: group.name,
              newMemberEmail: trimmedEmail
            }
            this.groupService.addMemberToGroup(newMember);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          placeholder: this.translate.instant("alerts.group.add_member.placeholder"),
          name: "memberEmail",
          type: "email",
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector("ion-alert input") as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  async showGroupMembers(groupId: number, groupOwnerName: string, slidingItem: IonItemSliding) {
    slidingItem.close();
    const modal = this.modalCtrl.create({
      component: GroupmembersPage,
      componentProps: {
        'groupId': groupId,
        'groupOwnerName': groupOwnerName
      }
    });

    (await modal).onWillDismiss().then(groupWithMembers => {
      if (!groupWithMembers.data) {
        return;
      }
    })

    return (await modal).present();
  }

}
