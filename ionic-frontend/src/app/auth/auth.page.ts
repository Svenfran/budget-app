import { Component, OnInit } from '@angular/core';
import { AuthResponseData, AuthService } from './auth.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadingController, MenuController, ToastController } from '@ionic/angular';
import { Observable } from 'rxjs';
import { AlertService } from '../services/alert.service';

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
              private alertService: AlertService) { }

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
    console.log("Reset password...");
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
        // console.log(errRes);
        let header = !this.isLogin ? 'Registrierung fehlgeschlagen' : 'Anmeldung fehlgeschlagen';
        let message = 'Passwort oder Email falsch.';
        if (errRes.status !== 403 && errRes.error.includes(userEmail)) {
          message = 'Ein Benutzer mit dieser Email-Adresse existiert bereits.';
        } else if (errRes.status !== 403 && errRes.error.includes(userName)) {
          message = 'Ein Benutzer mit diesem Benutzernamen existiert bereits.';
        } else {
          message = "Es ist ein allgemeiner Fehler aufgetreten. Versuche es bitte noch einmal.";
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
