import { Component, OnInit } from '@angular/core';
import { AuthResponseData, AuthService } from './auth.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AlertController, LoadingController, MenuController, ToastController } from '@ionic/angular';
import { Observable } from 'rxjs';
import { AlertService } from '../services/alert.service';
import { UserprofileService } from '../services/userprofile.service';
import { ResetPasswordDto } from '../models/reset-password-dto';

@Component({
  selector: 'app-auth',
  templateUrl: './auth.page.html',
  styleUrls: ['./auth.page.scss'],
})
export class AuthPage implements OnInit {
  
  isLogin = true;
  form: FormGroup;
  isLoading = false;
  showPassword: boolean = false;

  constructor(private authService: AuthService,
              private loadingCtrl: LoadingController,
              private router: Router,
              private fb: FormBuilder,
              private menuCtrl: MenuController,
              private toastCtrl: ToastController,
              private alertCtrl: AlertController,
              private alertService: AlertService,
              private userprofileService: UserprofileService) { }

  ngOnInit() {
    this.form = this.fb.group({
      userName: [''],
      userEmail: ['',[ Validators.required, Validators.pattern(/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i) ]],
      password: ['',[ Validators.required, Validators.minLength(6) ]],
    });
    
    this.menuCtrl.enable(false, 'm1');
  }

  ionViewDidLeave() {
    this.menuCtrl.enable(true, 'm1');
  }

  passwortReset() {
    // TODO: implement password reset
    this.alertCtrl.create({
      header: "Passwort vergessen?",
      message: "Bitte gib eine gültige E-Mail-Adresse an.",
      buttons: [{
        text: "Abbrechen",
        role: "cancel"
      }, {
        text: "reset",
        handler: (data) => {
          let email = data.email.trim();
          const resetDto = new ResetPasswordDto(email);
          if (EmailValidator.isNotValid(email)) {
            let header = "Fehlerhafte E-Mail-Adresse!";
            let message = "Bitte gib eine gültige E-mail-Adresse an.";
            this.alertService.showAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: "Passwort Reset..."
          }).then(loadingEl => {
            loadingEl.present(),
            this.userprofileService.resetUserPassword(resetDto).subscribe(() => {
              loadingEl.dismiss();
              console.log("Passwort Reset für " + email);
              let header = "Passwort Reset";
              let message = "Wir haben eine E-Mail mit einem temporären Passwort an die E-Mail-Adresse "
                            + email + " gesendet. Bitte melde dich an und ändere dein Passwort.";
              this.alertService.showAlert(header, message);
            }, errRes => {
              loadingEl.dismiss();
              if (errRes.error.includes(email)) {
                loadingEl.dismiss();
                let header = "Fehlerhafte E-Mail-Adresse!";
                let message = `Ein Benutzer mit der E-Mail-Adresse "${email}" existiert nicht.`
                this.alertService.showAlert(header, message);
              }
              if (errRes.error === "Invalid Email") {
                loadingEl.dismiss();
                let header = "Fehlerhafte E-Mail-Adresse!";
                let message = "Bitte gib eine gültige E-Mail-Adresse an.";
                this.alertService.showAlert(header, message);
              }
            });
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

  toggleShow() {
    this.showPassword = !this.showPassword;
  }

  onSwitchAuthMode() {
    this.isLogin = !this.isLogin; 
    if (this.isLogin) {
      this.form.controls['userName'].clearValidators();
    } else {
      this.form.controls['userName'].setValidators([Validators.required]);
    }
    this.form.controls['userName'].updateValueAndValidity();
  }

  authenticate(userName: string, userEmail: string, password: string) {
    this.isLoading = true;
    this.loadingCtrl
    .create({ keyboardClose: true, message: 'Anmelden...' })
    .then(loadingEl => {
      loadingEl.present();
      let authObs: Observable<AuthResponseData>;
      if (this.isLogin) {
        authObs = this.authService.login(userEmail, password);
      } else if (!this.isLogin) {
        authObs = this.authService.register(userName, userEmail, password);
      }
      authObs.subscribe(resData => {
        // console.log(resData);
        this.isLoading = false;
        loadingEl.dismiss();
        this.router.navigateByUrl('/domains/tabs/overview', { replaceUrl: true });
        let message = 'Erfolgreich angemeldet!'
        this.showToast(message);
      }, errRes => {
        // console.log(errRes.error);
        let header = !this.isLogin ? 'Registrierung fehlgeschlagen' : 'Anmeldung fehlgeschlagen';
        // for Authentication
        let message = 'Passwort oder Email falsch.';
        // for Registration
        if (errRes.status !== 403 && errRes.error.includes(userEmail)) {
          message = 'Ein Benutzer mit dieser Email-Adresse existiert bereits.';
        } else if (errRes.status !== 403 && errRes.error.includes(userName)) {
          message = 'Ein Benutzer mit diesem Benutzernamen existiert bereits.';
        }
        loadingEl.dismiss();
        this.alertService.showAlert(header, message);
      });
    });
  }

  onSubmit() {
    if (!this.form.valid) {
      return;
    }
    const userName = this.form.value.userName;
    const userEmail = this.form.value.userEmail;
    const password = this.form.value.password;
  
    this.authenticate(userName, userEmail, password)
    this.form.reset();
  }

  private showToast(message: string) {
    this.toastCtrl
      .create({
        message: message,
        duration: 1500,
        position: 'bottom'
      })
      .then(toastEl => toastEl.present());

  }

  get userName() {return this.form.get('userName');}
  get userEmail() {return this.form.get('userEmail');}
  get password() {return this.form.get('password');}

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