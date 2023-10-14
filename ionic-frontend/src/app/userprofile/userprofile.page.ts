import { Component, OnInit } from '@angular/core';
import { AlertController, LoadingController } from '@ionic/angular';
import { UserprofileService } from '../services/userprofile.service';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user';
import { Router } from '@angular/router';

@Component({
  selector: 'app-userprofile',
  templateUrl: './userprofile.page.html',
  styleUrls: ['./userprofile.page.scss'],
})
export class UserprofilePage implements OnInit {

  userName: string;
  userEmail: string;
  user: User;


  constructor(
    private alertCtrl: AlertController,
    private loadingCtrl: LoadingController,
    private userProfileService: UserprofileService,
    private authService: AuthService,
    private router: Router) { }

  ngOnInit() {
    this.getCurrentUser();
  }

  changeUsername() {
    this.alertCtrl.create({
      header: "Benutzername ändern",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Ändere Benutzername..."
          }).then(loadingEl => {
            loadingEl.present(),
            setTimeout("", 3000);
            loadingEl.dismiss();
            this.userName = data.userName;
            // TODO: write new user name into storage
          })
        }
      }],
      inputs: [
        {
          name: "userName",
          placeholder: "Benutzername"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  changeEmail() {
    this.alertCtrl.create({
      header: "E-Mail Adresse ändern",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Ändere Email..."
          }).then(loadingEl => {
            loadingEl.present(),
            setTimeout("", 3000);
            loadingEl.dismiss();
            this.userEmail = data.email;
          })
        }
      }],
      inputs: [
        {
          name: "email",
          placeholder: "E-Mail"
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }
  
  changePassword() {
    this.alertCtrl.create({
      header: "Passwort ändern",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          this.loadingCtrl.create({
            message: "Ändere Passwort..."
          }).then(loadingEl => {
            loadingEl.present(),
            setTimeout("", 3000);
            loadingEl.dismiss();
            console.log(data.passwordOld);
            console.log(data.passwordNew);
          })
        }
      }],
      inputs: [
        {
          name: "passwordOld",
          placeholder: "Altes Passwort"
        },
        {
          name: "passwordNew",
          placeholder: "Neues Passwort"
        }

      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField: HTMLElement = document.querySelector("ion-alert input");
      inputField.focus();
    }));
  }

  deleteProfile() {
    this.alertCtrl.create({
      header: "Profil löschen",
      message: "Möchtest du dein Profil wirklich löschen inkl. aller Gruppen und Ausgaben?",
      buttons: [{
        text: 'Nein'
      }, {
        text: 'Ja',
        handler: () => {
          this.loadingCtrl.create({
            message: 'Lösche Profil...'
          }).then(loadingEl => {
            loadingEl.present(),
            this.userProfileService.deleteUserProfile(this.user.id).subscribe(() => {
              loadingEl.dismiss();
              this.authService.logout();
              this.router.navigateByUrl("/auth", { replaceUrl: true });
            })
          })
        }
      }]
    }).then(alertEl => alertEl.present());
  }

  getCurrentUser() {
    this.authService.user.subscribe(user => {
      this.user = user;
      this.userName = user.name;
      this.userEmail = user.email;
    })
    return this.user;
  }

}
