import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { LoadingController, MenuController } from '@ionic/angular';
import { AlertService } from '../service/alert.service';
import { UserprofileService } from '../userprofile/service/userprofile.service';
import { User } from '../auth/user';
import { PasswordChangeDto } from '../userprofile/model/password-change-dto';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-password-change',
  templateUrl: './password-change.page.html',
  styleUrls: ['./password-change.page.scss'],
  standalone: false
})
export class PasswordChangePage implements OnInit {

  public form!: FormGroup;
  public isLoading = false;
  public user!: User;
  public showPassOld: boolean = false;
  public showPassNew: boolean = false;
  public showPassNewConfirmed: boolean = false;

  constructor(
    private authService: AuthService,
    private fb: FormBuilder,
    private userprofileService: UserprofileService,
    private loadingCtrl: LoadingController,
    private alertService: AlertService,
    private menuCtrl: MenuController,
    private translate: TranslateService
  ) { }

  ngOnInit() {
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
    });

    this.form = this.fb.group({
      oldPassword: ['', [Validators.required, Validators.minLength(6), Validators.pattern(/^\S*$/)]],
      newPassword: ['', [Validators.required, Validators.minLength(6), Validators.pattern(/^\S*$/)]],
      newPasswordConfirmed: ['', [Validators.required, Validators.minLength(6), Validators.pattern(/^\S*$/)]],
    });
    
  }

  ionViewDidEnter() {
    this.menuCtrl.enable(false);
  }

  get oldPassword() {return this.form.get('oldPassword');}
  get newPassword() {return this.form.get('newPassword');}
  get newPasswordConfirmed() {return this.form.get('newPasswordConfirmed');}

  toggleShow(str: string) {
    if (str === "O") {
      this.showPassOld = !this.showPassOld;
    }
    if (str === "N") {
      this.showPassNew = !this.showPassNew;
    }
    if (str === "NC") {
      this.showPassNewConfirmed = !this.showPassNewConfirmed;
    }
  }

  onSubmit() {
    if (!this.form.valid) {
      return;
    }

    if (this.passwordConfirmed()) {
      const passwordChangeData: PasswordChangeDto = {
        userId: this.user.id,
        oldPassword: this.form.value.oldPassword,
        newPassword: this.form.value.newPassword
      }
      this.changePassword(passwordChangeData);
    } else {
      let header = this.translate.instant("alerts.user_profile.change_password.error_message_page.header");
      let message = this.translate.instant("alerts.user_profile.change_password.error_message_page.message");
      this.alertService.showErrorAlert(header, message);
    }
    this.form.reset();
  }

  passwordConfirmed(): boolean {
    return this.form.value.newPassword === this.form.value.newPasswordConfirmed;
  }

  changePassword(passwordChangeData: PasswordChangeDto) {
    this.isLoading = true;
    this.loadingCtrl.create({ keyboardClose: true, message: this.translate.instant("alerts.user_profile.change_password.loading") })
    .then(loadingEl => {
      loadingEl.present();
      this.userprofileService.changePassword(passwordChangeData);
      loadingEl.dismiss();
      this.form.reset();
    });
  }
}
