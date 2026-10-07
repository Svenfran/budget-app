import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { App } from '@capacitor/app';
import { AlertController, LoadingController, MenuController } from '@ionic/angular';
import { TranslateService } from '@ngx-translate/core';
import { INIT_NUMBERS } from 'src/app/constants/default-values';
import { Group } from 'src/app/model/group';
import { GroupService } from 'src/app/service/group.service';

@Component({
  selector: 'app-no-group',
  templateUrl: './no-group.page.html',
  styleUrls: ['./no-group.page.scss'],
  standalone: false
})
export class NoGroupPage implements OnInit {

  constructor(
    private menuCtrl: MenuController,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private groupService: GroupService,
    private router: Router,
    private translate: TranslateService
  ) {}

  ngOnInit() {
    this.menuCtrl.enable(false);
  }

  ionViewWillLeave() {
    this.menuCtrl.enable(true);
  }

  ionViewDidEnter() {
    this.menuCtrl.enable(false);
  }

  exitApp() {
    App.exitApp();
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
            this.groupService.addGroup(newGroup, this.router.url);
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
  
}
