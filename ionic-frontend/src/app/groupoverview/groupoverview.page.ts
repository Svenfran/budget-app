import { Component, Input, OnInit } from '@angular/core';
import { AlertController, IonItemSliding, LoadingController, ModalController, ToastController } from '@ionic/angular';
import { GroupMembersPage } from '../group-members/group-members.page';
import { Group } from '../models/group';
import { GroupMembers } from '../models/group-members';
import { GroupOverview } from '../models/group-overview';
import { GroupSideNav } from '../models/group-side-nav';
import { NewMemberDto } from '../models/new-member-dto';
import { UserDto } from '../models/user';
import { CartService } from '../services/cart.service';
import { GroupService } from '../services/group.service';
import { SpendingsOverviewService } from '../services/spendings-overview.service';
import { StorageService } from '../services/storage.service';
import { from, of } from 'rxjs';
import { Router } from '@angular/router';
import { NavigationService } from '../services/navigation.service';
import { AuthService } from '../auth/auth.service';
import { AlertService } from '../services/alert.service';

@Component({
  selector: 'app-groupoverview',
  templateUrl: './groupoverview.page.html',
  styleUrls: ['./groupoverview.page.scss'],
})
export class GroupoverviewPage implements OnInit {

  groupOverviewList: GroupOverview[] = [];
  groupMembers: GroupMembers;
  isLoading: boolean = false;
  userName: string;
  currentUser: UserDto;
  activeGroup: GroupSideNav;
  groupIsDeleted: boolean = false;

  constructor(
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private modalCtrl: ModalController,
    private toastCtrl: ToastController,
    private cartService: CartService,
    private spendingsService: SpendingsOverviewService,
    private storageService: StorageService,
    private navigationService: NavigationService,
    private router: Router,
    private authService: AuthService,
    private alertService: AlertService
    ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.getGroupsForOverview();
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroup = group;
    })
  }


  getGroupsForOverview() {
    this.groupService.groupModified.subscribe(() => {
      this.isLoading = true;
      this.groupService.getGroupsForOverview().subscribe(groups => {
        this.groupOverviewList = groups;
        this.isLoading = false;
      }, errRes => {
        this.isLoading = false;
        if (errRes.status === 0) {
          this.alertService.showAlertSeverUnavailable();
        }
      })
    })
    this.groupService.setGroupModified(false);
  }

  navigate() {
    if (this.groupIsDeleted && this.navigationService.getPreviousUrl().includes("shoppinglist")) {
      this.router.navigate(['/domains/tabs/overview']);
    }
  }

  onDelete(groupId: number, groupName: string, slidingItem: IonItemSliding) {
    this.groupIsDeleted = true;
    slidingItem.close();
    this.alertCtrl.create({
      header: 'Löschen',
      message: `Möchtest du die Gruppe "${groupName}" wirklich löschen inkl. aller Mitglieder und gespeicherten Ausgaben?`,
      buttons: [{
        text: 'Nein'
      }, {
        text: 'Ja',
        handler: () => {
          this.loadingCtrl.create({
            message: 'Lösche Gruppe...'
          }).then(loadingEl => {
            loadingEl.present(),
            this.groupService.deleteGroup(groupId).subscribe(() => {
              loadingEl.dismiss();
              this.groupOverviewList = this.groupOverviewList.filter(group => group.id !== groupId);
              this.groupService.setGroupModified(true);
              this.groupService.activeGroup.subscribe(activeGroup => {
                if(this.groupOverviewList.length > 0 && (activeGroup.id === groupId)) {
                  const newGroup = new Group(
                    this.groupOverviewList[0].id,
                    this.groupOverviewList[0].name,
                    this.groupOverviewList[0].dateCreated
                  )
                  this.groupService.setActiveGroup(newGroup);
                  this.storageService.setActiveGroup(newGroup);
                } else if (this.groupOverviewList.length <= 0 && activeGroup.id === groupId) {
                  this.groupService.setActiveGroup(null);
                  this.groupService.activeGroup.subscribe(actGroup => {
                    this.storageService.setActiveGroup(actGroup)
                  })
                }
              });
              this.groupService.activeGroup.subscribe(group => {
                if (group.id !== null) {
                  this.spendingsService.setSpendingsModified(true);
                }
              });
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              }
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  onCreateGroup() {
    this.alertCtrl.create({
      header: "Neue Gruppe:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Erstelle Gruppe..."
          }).then(loadingEl => {
            let newGroup = new Group(null, data.groupName, null);
            this.groupService.addGroup(newGroup).subscribe((group) => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
              this.groupService.setActiveGroup(group);
              this.activeGroup = group;
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              }
            })
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          placeholder: "Gruppenname"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onAddMember(groupId: number, groupName: string, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: "Neues Gruppenmitglied:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          // console.log(data.memberEmail.match("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,4}$")[0] == data.memberEmail);
          this.loadingCtrl.create({
            message: "Füge Benutzer hinzu..."
          }).then(loadingEl => {
            let newMember = new NewMemberDto(
              groupId,
              groupName,
              data.memberEmail.trim()
            )
            this.groupService.addMemberToGroup(newMember).subscribe(() => {
              this.getGroupsForOverview();
              this.cartService.setCartModified(true);
              this.spendingsService.setSpendingsModified(true);
              loadingEl.dismiss();
              let message = "Benutzer wurde zur Gruppe " + groupName + " hinzugefügt"
              this.showToast(message);
            }, errRes => {
              let message: string;
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              } else if (errRes.error.includes(newMember.newMemberEmail)) {
                message = "Benutzer existiert nicht."
              } else if (errRes.error.includes("New member equals group owner")) {
                message = "Neues Mitglied und Gruppenersteller sind identisch"
              } else if (errRes.error.includes("Member already exists")) {
                message = "Der Benutzer ist bereits Mitglied"
              } else {
                message = "Es ist ein Fehler aufgetreten"
              }
              this.showToast(message);
            }) 
          })
        }
      }],
      inputs: [
        {
          placeholder: "E-Mail Adresse",
          name: "memberEmail",
          type: "email"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  onUpdate(groupId: number, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: "Gruppenname:",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Bearbeite Gruppe..."
          }).then(loadingEl => {
            let updatedGroup = new Group(groupId, data.groupName, null);
            this.groupService.updateGroup(updatedGroup).subscribe(() => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
              this.groupService.setActiveGroup(updatedGroup);

              // let group = new GroupOverview(
              //   groupId,
              //   data.groupName,
              //   this.userName,
              //   this.groupOverviewList.filter(g => g.id == groupId)[0].memberCount
              // )

              // let index = this.groupOverviewList.indexOf(
              //   this.groupOverviewList.filter(g => g.id == group.id)[0]
              // )
              
              // if (this.groupOverviewList[index].name != data.groupName) {
              //   this.groupOverviewList[index] = group;
              // }
              
            }, errRes => {
              if (errRes.status === 0) {
                loadingEl.dismiss();
                this.alertService.showAlertSeverUnavailable();
              }
            })
          })
        }
      }],
      inputs: [
        {
          name: "groupName",
          value: this.groupOverviewList.filter(g => g.id == groupId)[0].name
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  async showMembers(groupId: number, groupOwnerName: string, slidingItem: IonItemSliding) {
    slidingItem.close();
    const modal = this.modalCtrl.create({
      component: GroupMembersPage,
      componentProps: {
        'groupId': groupId,
        'groupOwnerName': groupOwnerName
      }
    });

    (await modal).onDidDismiss().then((groupWithMembers) => {
      let group = new GroupOverview(
        groupWithMembers.data.id,
        groupWithMembers.data.name,
        groupWithMembers.data.ownerName,
        groupWithMembers.data.members.length,
        groupWithMembers.data.dateCreated
      )
 
      let index = this.groupOverviewList.indexOf(
        this.groupOverviewList.filter(g => g.id == group.id)[0]
      )

      if (this.groupOverviewList[index].memberCount !== group.memberCount) {
        // this.groupOverviewList[index] = group;
        this.groupService.setGroupModified(true);
      }

      // if ((this.groupOverviewList.length - 1) <= 0 && this.activeGroup.id == group.id) {
      //   this.groupService.setActiveGroup(null);
      // }
      
    });
    return (await modal).present();
  }


  private showToast(message: string) {
    this.toastCtrl.create({
      message: message,
      duration: 3000,
      position: 'bottom'
    }).then(toastEl => toastEl.present());
  }

  getCurrentUser() {
    this.authService.user.subscribe(user => {
      this.userName = user.name;
      this.currentUser = new UserDto(user.id, user.name);
    })
    return this.userName;
  }
}
