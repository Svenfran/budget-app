import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { User } from '../auth/user';
import { AuthService } from '../auth/auth.service';
import { LoadingController } from '@ionic/angular';
import { PasswordChangeDto } from '../models/password-change-dto';
import { UserprofileService } from '../services/userprofile.service';
import { AlertService } from '../services/alert.service';

@Component({
  selector: 'app-passwordchange',
  templateUrl: './passwordchange.page.html',
  styleUrls: ['./passwordchange.page.scss'],
})
export class PasswordchangePage implements OnInit {

  form: FormGroup;
  isLoading = false;
  showPasswordO: boolean = false;
  showPasswordN: boolean = false;
  showPasswordNC: boolean = false;
  user: User;
  
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private userprofileService: UserprofileService,
    private loadingCtrl: LoadingController,
    private alertService: AlertService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.form = this.fb.group({
      oldPassword: ['',[ Validators.required, Validators.minLength(6) ]],
      newPassword: ['',[ Validators.required, Validators.minLength(6) ]],
      newPasswordConfirmed: ['',[ Validators.required, Validators.minLength(6) ]],
    });
  }

  toggleShow(str: String) {
    if (str === "O") {
      this.showPasswordO = !this.showPasswordO;
    }
    if (str === "N") {
      this.showPasswordN = !this.showPasswordN;
    }
    if (str === "NC") {
      this.showPasswordNC = !this.showPasswordNC;
    }
  }

  onSubmit() {
    if (!this.form.valid) {
      return;
    }

    if (this.passwordConfirmed()) {
      const passwordChangeData = new PasswordChangeDto(
        this.user.id,
        this.form.value.oldPassword,
        this.form.value.newPassword
      )
      this.changePassword(passwordChangeData);
    } else {
      let header = "Falsches Passwort!";
      let message = "Das neue Passwort konnte nicht bestätigt werden. Bitte versuche es noch einmal";
      this.alertService.showAlert(header, message);
    }
    this.form.reset();
  }

  changePassword(passwordChangeData: PasswordChangeDto) {
    this.isLoading = true;
    this.loadingCtrl
    .create({ keyboardClose: true, message: 'Ändere Passwort...'})
    .then(loadingEl => {
      loadingEl.present();
      this.userprofileService.changeUserPassword(passwordChangeData).subscribe(() => {
        this.isLoading = false;
        loadingEl.dismiss();
        this.authService.logout();
      }, errRes => {
        if (errRes.error === "Incorrect password") {
          loadingEl.dismiss();
          this.isLoading = false;
          let header = "Falsches Passwort!";
          let message = "Das angegebene Passwort ist nicht korrekt. Bitte versuche es noch einmal";
          this.alertService.showAlert(header, message);
        }
      });
      this.form.reset();
    });
  }

  passwordConfirmed(): boolean {
    return this.form.value.newPassword === this.form.value.newPasswordConfirmed;
  }

  getCurrentUser() {
    this.authService.user.subscribe(user => {
      this.user = user;
    })
    return this.user;
  }

  get oldPassword() {return this.form.get('oldPassword');}
  get newPassword() {return this.form.get('newPassword');}
  get newPasswordConfirmed() {return this.form.get('newPasswordConfirmed');}
}
