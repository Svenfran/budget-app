import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user';
import { AlertController, LoadingController, MenuController } from '@ionic/angular';
import { UserprofileService } from './service/userprofile.service';
import { AlertService } from '../service/alert.service';
import { UserDto } from '../model/user-dto';
import { EmailValidator } from '../Validator/email-validator';
import { INIT_NUMBERS } from '../constants/default-values';
import { Router } from '@angular/router';
import { GroupService } from '../service/group.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-userprofile',
  templateUrl: './userprofile.page.html',
  styleUrls: ['./userprofile.page.scss'],
  standalone: false
})
export class UserprofilePage implements OnInit {

  public user!: User;
  public noGroups = this.groupService.hasNoGroups;

  constructor(
    private authService: AuthService,
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private userProfileService: UserprofileService,
    private alertService: AlertService,
    private menuCtrl: MenuController,
    private router: Router,
    private groupService: GroupService,
    private translate: TranslateService
  ) { }

  ngOnInit() {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
    })
  }

  ionViewDidEnter() {
    this.menuCtrl.enable(false);
  }

  ionViewWillLeave() {
    if (!this.router.url.includes("no-group")) {
      this.menuCtrl.enable(true);
    }
  }

  changeUserName() {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.user_profile.change_username.header"),
      buttons: [{
        text: this.translate.instant("alerts.user_profile.change_username.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.user_profile.change_username.ok"),
        handler: (data) => {
          if (data.userName === undefined || data.userName === null || data.userName.trim() === "") {
            let header = this.translate.instant("alerts.user_profile.change_username.error_message_page.header");
            let message = this.translate.instant("alerts.user_profile.change_username.error_message_page.message");
            this.alertService.showErrorAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.user_profile.change_username.loading")
          }).then(loadingEl => {
            loadingEl.present();
            const user: UserDto = {
              id: this.user.id,
              userName: data.userName,
              userEmail: this.user.email
            }
            this.userProfileService.changeUserName(user);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "userName",
          placeholder: this.translate.instant("alerts.user_profile.change_username.placeholder"),
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH_50
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector('ion-alert input') as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  changeUserEmail() {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.user_profile.change_email.header"),
      buttons: [{
        text: this.translate.instant("alerts.user_profile.change_email.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.user_profile.change_email.ok"),
        handler: (data) => {
          const email = data.email.trim();
          if (EmailValidator.isNotValid(email)) {
            let header = this.translate.instant("alerts.user_profile.change_email.error_message_page.header");
            let message = this.translate.instant("alerts.user_profile.change_email.error_message_page.message");
            this.alertService.showErrorAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.user_profile.change_email.loading")
          }).then(loadingEl => {
            loadingEl.present();
            const user: UserDto = {
              id: this.user.id,
              userName: this.user.name,
              userEmail: data.email
            }
            this.userProfileService.changeUserEmail(user);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "email",
          placeholder: this.translate.instant("alerts.user_profile.change_email.placeholder"),
          type: "email",
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH_50
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector('ion-alert input') as HTMLElement;
      if (inputField) {
        inputField.focus();
      }
    }));
  }

  deleteUserProfile() {
    this.alertCtrl.create({
      header: this.translate.instant("alerts.user_profile.delete_profile.header"),
      message: this.translate.instant("alerts.user_profile.delete_profile.message"),
      buttons: [{
        text: this.translate.instant("alerts.user_profile.delete_profile.cancel"),
        role: "cancel"
      }, {
        text: this.translate.instant("alerts.user_profile.delete_profile.ok"),
        handler: () => {
          this.loadingCtrl.create({
            message: this.translate.instant("alerts.user_profile.delete_profile.loading")
          }).then(loadingEl => {
            loadingEl.present();
            this.userProfileService.deleteUserProfile(this.user.id);
            loadingEl.dismiss();
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  onLogout() {
    this.authService.logout();
    this.router.navigateByUrl("/auth", { replaceUrl: true });
  }
}
