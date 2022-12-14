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

  constructor(
    private groupService: GroupService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private modalCtrl: ModalController,
    private toastCtrl: ToastController,
    private cartService: CartService,
    private spendingsService: SpendingsOverviewService
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
      })
    })
    this.groupService.setGroupModified(false);
  }


  onDelete(groupId: number, groupName: string, slidingItem: IonItemSliding) {
    slidingItem.close();
    this.alertCtrl.create({
      header: 'L??schen',
      message: `M??chtest du die Gruppe "${groupName}" wirklich l??schen inkl. aller Mitglieder und gespeicherten Ausgaben?`,
      buttons: [{
        text: 'Nein'
      }, {
        text: 'Ja',
        handler: () => {
          this.loadingCtrl.create({
            message: 'L??sche Gruppe...'
          }).then(loadingEl => {
            loadingEl.present(),
            this.groupService.deleteGroup(groupId).subscribe(() => {
              loadingEl.dismiss();
              this.groupOverviewList = this.groupOverviewList.filter(group => group.id !== groupId);
              this.groupService.setGroupModified(true);
              this.groupService.activeGroup.subscribe(activeGroup => {
                if(this.groupOverviewList.length > 0 && (activeGroup.id === groupId)) {
                    this.groupService.setActiveGroup(new Group(
                      this.groupOverviewList[0].id,
                      this.groupOverviewList[0].name,
                      this.groupOverviewList[0].dateCreated
                    ))
                } else if (this.groupOverviewList.length <= 0 && activeGroup.id === groupId) {
                  this.groupService.setActiveGroup(null);
                }
              })
              this.spendingsService.setSpendingsModified(true);
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
            this.groupService.addGroup(newGroup).subscribe(() => {
              loadingEl.dismiss();
              this.groupService.setGroupModified(true);
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
            message: "F??ge Benutzer hinzu..."
          }).then(loadingEl => {
            let newMember = new NewMemberDto(
              groupId,
              groupName,
              data.memberEmail
            )
            this.groupService.addMemberToGroup(newMember).subscribe(() => {
              this.getGroupsForOverview();
              this.cartService.setCartModified(true);
              this.spendingsService.setSpendingsModified(true);
              loadingEl.dismiss();
              let message = "Benutzer wurde zur Gruppe " + groupName + " hinzugef??gt"
              this.showToast(message);
            }, errRes => {
              loadingEl.dismiss();
              let message: string;
              if (errRes.error.includes("User not found")) {
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
          name: "memberEmail"
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

      if ((this.groupOverviewList.length - 1) <= 0 && this.activeGroup.id == group.id) {
        this.groupService.setActiveGroup(null);
      }
      
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
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
      this.currentUser = user;
    })
  }
}
