import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { UserDto } from 'src/app/model/user-dto';
import { AlertService } from 'src/app/service/alert.service';
import { environment } from 'src/environments/environment';
import { PasswordChangeDto } from '../model/password-change-dto';
import { ResetPasswordDto } from '../model/reset-password-dto';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class UserprofileService {

  private user!: User;

  private apiBaseUrl = environment.apiBaseUrl;
  private deleteUserUrl = `${this.apiBaseUrl}/api/userprofile/delete`;
  private changeUserNameUrl = `${this.apiBaseUrl}/api/userprofile/update-username`;
  private changeUserEmailUrl = `${this.apiBaseUrl}/api/userprofile/update-usermail`;
  private changePasswordUrl = `${this.apiBaseUrl}/api/userprofile/update-password`;
  private resetPasswordUrl = `${this.apiBaseUrl}/api/userprofile/password-reset`;

  constructor(
    private http: HttpClient,
    private alertService: AlertService,
    private authService: AuthService,
    private router: Router,
    private translate: TranslateService
  ) { 
    this.authService.user.pipe().subscribe(user => {
      if (user) this.user = user;
    })
  }

  deleteUserProfile(userId: number): void {
    this.http
      .delete<void>(`${this.deleteUserUrl}/${userId}`)
      .subscribe({
        next: () => {
          console.log("user porfile successfully deleted");
          this.authService.logout();
          this.router.navigateByUrl("/auth", { replaceUrl: true });
        },
        error: (err) => {
          console.error("Error deleting user profile:", err);
        }
      })
  }

  changeUserName(userDto: UserDto): void {
    this.http
      .put<UserDto>(this.changeUserNameUrl, userDto)
      .subscribe({
        next: (result) => {
          this.authService.setUserData({
            id: result.id,
            name: result.userName,
            email: this.user.email,
            expirationDate: this.user.expirationDate,
            token: this.user.token ? this.user.token.slice(7) : ""
          })
        },
        error: (err) => {
          console.error("Error changing user name:", err);
          if (err.error.includes(userDto.userName.trim())) {
            let header = this.translate.instant("alerts.user_profile.change_username.error_message_service.exists.header");
            let message = this.translate.instant("alerts.user_profile.change_username.error_message_service.exists.message", {userName: userDto.userName.trim()});
            this.alertService.showErrorAlert(header, message);
          } else if (err.error.includes('User name is not allowed')) {
            let header = this.translate.instant("alerts.user_profile.change_username.error_message_service.not_allowed.header");
            let message = this.translate.instant("alerts.user_profile.change_username.error_message_service.not_allowed.message", {userName: userDto.userName.trim()});
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  changeUserEmail(userDto: UserDto): void {
    this.http
      .put<UserDto>(this.changeUserEmailUrl, userDto)
      .subscribe({
        next: (result) => {
          console.log("User email changed successfully");
          this.authService.logout();
          this.router.navigateByUrl("/auth", { replaceUrl: true });
        },
        error: (err) => {
          console.error("Error changing user email:", err);
          if (err.error.includes(userDto.userEmail)) {
            let header = this.translate.instant("alerts.user_profile.change_email.error_message_service.header");
            let message = this.translate.instant("alerts.user_profile.change_email.error_message_service.message");
            this.alertService.showErrorAlert(header, message);
          }
          if (err.error === "Invalid Email") {
            let header = this.translate.instant("alerts.user_profile.change_email.error_message_service.invalid_email.header");
            let message = this.translate.instant("alerts.user_profile.change_email.error_message_service.invalid_email.message");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  changePassword(passwordChangeData: PasswordChangeDto): void {
    this.http
      .put<any>(this.changePasswordUrl, passwordChangeData)
      .subscribe({
        next: (result) => {
          console.log("Password changed successfully");
          this.authService.logout();
          this.router.navigateByUrl("/auth", { replaceUrl: true });
        },
        error: (err) => {
          console.log("Error changing password:", err);
          if (err.error === "Incorrect password") {
            let header = this.translate.instant("alerts.user_profile.change_password.error_message_service.header");
            let message = this.translate.instant("alerts.user_profile.change_password.error_message_service.message");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  resetPassword(resetPasswordDto: ResetPasswordDto): void {
    this.http
      .put<void>(this.resetPasswordUrl, resetPasswordDto)
      .subscribe({
        next: () => {
          console.log("Password reset successfully");
          let header = this.translate.instant('alerts.reset_password.success.header');
          let message = this.translate.instant('alerts.reset_password.success.message', {email: resetPasswordDto.email});
          this.alertService.showErrorAlert(header, message);

        },
        error: (err) => {
          console.error("Error resetting password:", err);
          if (err.error.includes(resetPasswordDto.email)) {
            let header = this.translate.instant('alerts.reset_password.error_email_not_exists.header');
            let message = this.translate.instant('alerts.reset_password.error_email_not_exists.message', {email: resetPasswordDto.email});
            this.alertService.showErrorAlert(header, message);
          }
          if (err.error === "Invalid Email") {
            let header = this.translate.instant('alerts.reset_password.error_invalid_email.header');
            let message = this.translate.instant('alerts.reset_password.error_invalid_email.message');
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }
}
