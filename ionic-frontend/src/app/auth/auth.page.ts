import { Component, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthResponseData, AuthService } from './auth.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AlertController, IonPopover, LoadingController, MenuController, ToastController } from '@ionic/angular';
import { AlertService } from '../service/alert.service';
import { ResetPasswordDto } from '../userprofile/model/reset-password-dto';
import { EmailValidator } from '../Validator/email-validator';
import { UserprofileService } from '../userprofile/service/userprofile.service';
import { INIT_NUMBERS } from '../constants/default-values';
import { TranslateService } from '@ngx-translate/core';
import { LanguageService } from '../service/language.service';
import { StorageService } from '../service/storage.service';

@Component({
  selector: 'app-auth',
  templateUrl: './auth.page.html',
  styleUrls: ['./auth.page.scss'],
  standalone: false
})
export class AuthPage implements OnInit {
  @ViewChild('languagePopover') languagePopover!: IonPopover;

  public darkMode: boolean = true;
  public isLogin = true;
  public form!: FormGroup;
  public isLoading = false;
  public showPassword: boolean = false;
  public selectedLanguage = this.langService.currentLang;
  public availableLanguages = [
    { code: 'de', label: 'Deutsch', flag: '🇩🇪' },
    { code: 'en', label: 'English', flag: '🇬🇧' },
    { code: 'es', label: 'Español', flag: '🇪🇸' },
  ];

  constructor(
    private authService: AuthService,
    private loadingCtrl: LoadingController,
    private router: Router,
    private fb: FormBuilder,
    private menuCtrl: MenuController,
    private toastCtrl: ToastController,
    private alertService: AlertService,
    private alertCtrl: AlertController,
    private userProfileService: UserprofileService,
    private translate: TranslateService,
    private langService: LanguageService,
    private storageService: StorageService
  ) { }

  ngOnInit() {
    this.form = this.fb.group({
      userName: [''],
      userEmail: ['',[ Validators.required, Validators.pattern(/^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i) ]],
      password: ['',[ Validators.required, Validators.minLength(6), Validators.pattern(/^\S*$/) ]],
    });
    
    this.menuCtrl.enable(false, 'm1');
    
    this.storageService.getItem<boolean>('darkMode').then(res => {
      this.darkMode = res || false
    })
  }

  ionViewDidLeave() {
    this.menuCtrl.enable(true, 'm1');
  }

  toggleShow() {
    this.showPassword = !this.showPassword;
  }

  selectLanguage(code: string) {
    this.langService.setLanguage(code);
    this.languagePopover.dismiss();
  }

  getLanguageLabel(code: string): string {
    const lang = this.availableLanguages.find(l => l.code === code);
    return lang ? `${lang.flag} ${lang.label} (${lang.code.toUpperCase()})` : code;
  }

  resetPassword() {
    this.alertCtrl.create({
      header: this.translate.instant('alerts.forgot_password.header'),
      message: this.translate.instant('alerts.forgot_password.message'),
      buttons: [{
        text: this.translate.instant('alerts.forgot_password.cancel'),
        role: "cancel"
      }, {
        text: this.translate.instant('alerts.forgot_password.ok'),
        handler: (data) => {
          let email = data.email.trim();
          const resetDto: ResetPasswordDto = {email: email};
          if (EmailValidator.isNotValid(email)) {
            let header = this.translate.instant('alerts.forgot_password.error_message.header');
            let message = this.translate.instant('alerts.forgot_password.error_message.message');
            this.alertService.showErrorAlert(header, message);
            return
          }
          this.loadingCtrl.create({
            message: this.translate.instant('alerts.forgot_password.loading'),
          }).then(loadingEl => {
            loadingEl.present();
            this.userProfileService.resetPassword(resetDto);
            loadingEl.dismiss();
          })
        }
      }],
      inputs: [
        {
          name: "email",
          placeholder: this.translate.instant('alerts.forgot_password.placeholder'),
          type: "email",
          attributes: {
            maxlength: INIT_NUMBERS.MAX_LENGTH_50
          }
        }
      ]
    }).then(alertEl => alertEl.present().then(() => {
      const inputField = document.querySelector("ion-alert input") as HTMLElement;
      if (inputField) inputField.focus();
    }));
  }

  onSwitchAuthMode() {
    this.isLogin = !this.isLogin; 
    if (this.isLogin) {
      this.form.controls['userName'].clearValidators();
    } else {
      this.form.controls['userName'].setValidators([Validators.required, Validators.minLength(3)]);
    }
    this.form.controls['userName'].updateValueAndValidity();
  }

  authenticate(userName: string, userEmail: string, password: string) {
    const USER_EMAIL = userEmail.trim();
    const USER_NAME = userName ? userName.trim() : "";
    
    this.isLoading = true;
    this.loadingCtrl
    .create({ keyboardClose: true, message: this.translate.instant('alerts.authenticate.loading') })
    .then(loadingEl => {
      loadingEl.present();
      let authObs: Observable<AuthResponseData>;
      if (this.isLogin) {
        authObs = this.authService.login(USER_EMAIL, password);
      } else {
        authObs = this.authService.register(USER_NAME, USER_EMAIL, password);
      }
      authObs.subscribe(resData => {
        // console.log(resData);
        this.isLoading = false;
        loadingEl.dismiss();
        this.router.navigateByUrl('/domains/tabs/overview', { replaceUrl: true });
        let message = this.translate.instant('alerts.authenticate.message');
        this.showToast(message);
      }, errRes => {
        // console.log(errRes.error);
        let header = !this.isLogin 
          ? this.translate.instant('alerts.authenticate.error_messages.header_register') 
          : this.translate.instant('alerts.authenticate.error_messages.header_login');
        // for Authentication
        let message = this.translate.instant('alerts.authenticate.error_messages.message');
        // for Registration
        if (errRes.status === 0) {
          loadingEl.dismiss();
          this.alertService.showAlertSeverUnavailable();
        } else if (errRes.status !== 403 && errRes.error.includes(USER_EMAIL)) {
          message = this.translate.instant('alerts.authenticate.error_messages.message_email_exists');
        } else if (errRes.status !== 403 && errRes.error.includes(USER_NAME)) {
          message = this.translate.instant('alerts.authenticate.error_messages.message_username_exists');
        }
        loadingEl.dismiss();
        this.alertService.showErrorAlert(header, message);
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
