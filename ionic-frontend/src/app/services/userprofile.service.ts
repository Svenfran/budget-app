import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { UserDto } from '../models/user';
import { PasswordChangeDto } from '../models/password-change-dto';
import { ResetPasswordDto } from '../models/reset-password-dto';

@Injectable({
  providedIn: 'root'
})
export class UserprofileService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private deleteUserUrl = `${this.apiBaseUrl}/api/userprofile/delete`;
  private changeUserNameUrl = `${this.apiBaseUrl}/api/userprofile/update-username`;
  private changeUserEmailUrl = `${this.apiBaseUrl}/api/userprofile/update-usermail`;
  private changePasswordUrl = `${this.apiBaseUrl}/api/userprofile/update-password`;
  private resetPasswordUrl = `${this.apiBaseUrl}/api/userprofile/password-reset`;

  constructor(private http: HttpClient) { }


  deleteUserProfile(userId: number): Observable<void> {
    const deleteUserUrl = `${this.deleteUserUrl}/${userId}`;
    return this.http.delete<void>(deleteUserUrl);
  }

  changeUserName(userDto: UserDto): Observable<UserDto> {
    return this.http.put<UserDto>(this.changeUserNameUrl, userDto);
  }

  changeUserEmail(userDto: UserDto): Observable<UserDto> {
    return this.http.put<UserDto>(this.changeUserEmailUrl, userDto);
  }

  changeUserPassword(paswordChangeData: PasswordChangeDto): Observable<any> {
    return this.http.put<any>(this.changePasswordUrl, paswordChangeData);
  }

  resetUserPassword(resetPasswordDto: ResetPasswordDto): Observable<any> {
    return this.http.put<any>(this.resetPasswordUrl, resetPasswordDto);
  }
}
