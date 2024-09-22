import { Injectable } from '@angular/core';
import { AlertController, LoadingController } from '@ionic/angular';
import { GroupService } from './group.service';
import { StorageService } from './storage.service';
import { Group } from '../models/group';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class AlertService {

  private HEADER_SERVER_UNAVAILABLE: string = "Es ist ein Fehler aufgetreten";
  private MESSAGE_SERVER_UNAVAILABLE: string = "Der Server ist momentan nicht erreichbar. Bitte versuche es in ein paar Minuten noch einmal.";

  activeGroup: Group;

  constructor(
    private loadingCtrl: LoadingController,
    private alertCtrl: AlertController,
    private groupService: GroupService,
    private storageService: StorageService,
    private router: Router
  ) { 
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroup = group;
    })
  }

  createGroup() {
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
              this.storageService.setActiveGroup(this.activeGroup);
            })
            if (this.router.url.includes("shoppinglist")) {
              this.router.navigate(['/domains/tabs/overview']);
            }
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

  showAlert(header: string, message: string) {
    this.alertCtrl
      .create({
        header: header,
        message: message,
        buttons: ['Ok']
      })
      .then(alertEl => alertEl.present());
  }

  showAlertSeverUnavailable() {
    this.alertCtrl
      .create({
        header: this.HEADER_SERVER_UNAVAILABLE,
        message: this.MESSAGE_SERVER_UNAVAILABLE,
        buttons: ['Ok']
      })
      .then(alertEl => alertEl.present());
  }
}

