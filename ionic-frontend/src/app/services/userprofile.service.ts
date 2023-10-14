import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UserprofileService {

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private deleteUserUrl = `${this.apiBaseUrl}/api/userprofile/delete`;

  constructor(private http: HttpClient) { }


  deleteUserProfile(userId: number): Observable<void> {
    const deleteUserUrl = `${this.deleteUserUrl}/${userId}`;
    return this.http.delete<void>(deleteUserUrl);
  }
}
