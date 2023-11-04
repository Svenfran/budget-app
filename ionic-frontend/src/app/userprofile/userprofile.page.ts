import { Component, OnInit } from '@angular/core';
import { AlertController, LoadingController } from '@ionic/angular';
import { UserprofileService } from '../services/userprofile.service';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user';
import { Router } from '@angular/router';
import { UserDto } from '../models/user';
import { StorageService } from '../services/storage.service';
import { AlertService } from '../services/alert.service';


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
    private router: Router,
    private storageService: StorageService,
    private alertService: AlertService
    ) { }

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
          if (data.userName === "" || data.userName === undefined || data.userName === null) {
            let header = "Fehlerhafter Benutzername!";
            let message = `Der Benutzername darf nicht leer sein.`
            this.alertService.showAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: "Ändere Benutzername..."
          }).then(loadingEl => {
            loadingEl.present(),
            this.userProfileService.changeUserName(new UserDto(this.user.id, data.userName)).subscribe(res => {
              this.userName = res.userName;
              this.setUserData();
              loadingEl.dismiss();
            }, errRes => {
              if (errRes.error.includes(data.userName)) {
                loadingEl.dismiss();
                let header = "Fehlerhafter Benutzername!";
                let message = `Der Benutzername "${data.userName}" existiert bereits.`
                this.alertService.showAlert(header, message);
              }
            });          
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
      header: "E-Mail-Adresse ändern",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "ok",
        handler: (data) => {
          let email = data.email.trim();
          if (EmailValidator.isNotValid(email)) {
            let header = "Fehlerhafte E-Mail-Adresse!";
            let message = "Bitte gib eine gültige E-mail-Adresse an.";
            this.alertService.showAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: "Ändere Email..."
          }).then(loadingEl => {
            loadingEl.present(),
            this.userProfileService.changeUserEmail(new UserDto(this.user.id, this.user.name, email)).subscribe(res => {
              console.log(res);
              this.userEmail = email;
              loadingEl.dismiss();
              this.authService.logout();
            }, errRes => {
              if (errRes.error.includes(email)) {
                loadingEl.dismiss();
                let header = "Fehlerhafte E-Mail-Adresse!";
                let message = `Die E-Mail-Adresse "${email}" existiert bereits.`;
                this.alertService.showAlert(header, message);
              }
              if (errRes.error === "Invalid Email") {
                loadingEl.dismiss();
                let header = "Fehlerhafte E-Mail-Adresse!";
                let message = "Bitte gib eine gültige E-Mail-Adresse an.";
                this.alertService.showAlert(header, message);
              }
            })
          })
        }
      }],
      inputs: [
        {
          name: "email",
          placeholder: "E-Mail-Adresse",
          type: "email"
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

  private setUserData() {
    this.storageService.getData('authData').then(storedData => {
      const parsedData = JSON.parse(storedData.value) as {id: number, name: string, email: string, expirationDate: number, token: string};
      const parsedObject = JSON.parse(parsedData['data']);
      const authRes = new AuthResponseData(
        parsedObject.id,
        this.userName,
        parsedObject.email,
        parsedObject.expirationDate,
        parsedObject.token.substring(7)
      )
      this.authService.setUserData(authRes);
    });
  }
  
}

class EmailValidator {
  static isNotValid(email: string){
    let pattern = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;
    let result = pattern.test(email);
    
    if (!result) {
      return {
        'email:validation:fail' : true
      }
    }
    return null;
  }
}

class AuthResponseData {
  constructor(
    public id: number,
    public name: string,
    public email: string,
    public expirationDate: number,
    public token: string
  ) {}
}