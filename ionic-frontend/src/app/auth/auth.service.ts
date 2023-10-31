import { HttpClient } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, from } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { StorageService } from '../services/storage.service';
import { User } from './user';

export interface AuthResponseData {
  id: number,
  name: string,
  email: string,
  expirationDate: number,
  token: string
}

@Injectable({
  providedIn: 'root'
})
export class AuthService implements OnDestroy {

  private _user = new BehaviorSubject<User>(null);
  private apiBaseUrl = environment.apiBaseUrlExternal;
  private authUrl = `${this.apiBaseUrl}/api/auth/authenticate`;
  private registerUrl = `${this.apiBaseUrl}/api/auth/register`;
  private logoutUrl = `${this.apiBaseUrl}/api/auth/logout`;
  
  private activeLogoutTimer: any;
  
  get userIsAuthenticated() {
    return this._user.asObservable().pipe(
      map(user => {
        if (user) {
          return !!user.token;
        } else {
          return false;
        }
      })
    );
  }

  constructor(
    private http: HttpClient,
    private storageService: StorageService
    ) {}
 
  login(userEmail: string, password: string ) {
    return this.http.post<AuthResponseData>(this.authUrl,
      { email: userEmail, password: password } ).pipe(tap(this.setUserData.bind(this)));
  }

  register(userName: string, userEmail: string, password: string) {
    return this.http.post<AuthResponseData>(this.registerUrl, 
      { name: userName, email: userEmail, password: password} ).pipe(tap(this.setUserData.bind(this)));
  }

  userLogout(): Observable<any>{
    console.log("userLogout() was called!")
    let header = {};
    let token = localStorage.getItem('token');
    header = { "Authorization" : token };
    return this.http.post<any>(this.logoutUrl, null, { headers: header });
  }

  logout() {
    if (this.activeLogoutTimer) {
      clearTimeout(this.activeLogoutTimer);
    }
    this.userLogout().subscribe();
    this._user.next(null);
    this.storageService.removeData('authData');
    localStorage.removeItem('token');
  }

  ngOnDestroy(): void {
    if (this.activeLogoutTimer) {
      clearTimeout(this.activeLogoutTimer);
    }
  }

  get token() {
    return this._user.asObservable().pipe(
      map(user => {
        if (user) {
          return user.token;
        } else {
          return null;
        }
      })
    );
  }

  get userName() {
    return this._user.asObservable().pipe(
      map(user => {
        if (user) {
          return user.name;
        } else {
          return null;
        }
      })
    );
  }

  get user() {
    return this._user.asObservable().pipe(
      map(user => {
        if (user) {
          return user;
        } else {
          return null;
        }
      })
    );
  }

  autoLogin() {
    return from(this.storageService.getData('authData')).pipe(
      map(storedData => {
        if (!storedData || !storedData.value) {
          return null;
        }
        const parsedData = JSON.parse(storedData.value) as {id: number, name: string, email: string, expirationDate: number, token: string};
        const parsedObject = JSON.parse(parsedData['data']);

        if (parsedObject.expirationDate <= new Date().getTime()) {
          return null;
        }

        const user = new User(
          parsedObject.id,
          parsedObject.name,
          parsedObject.email,
          parsedObject.expirationDate,
          parsedObject.token
          );
        return user;
      }),
      tap(user => {
        if (user) {
          this._user.next(user);
          this.autoLogout(user.tokenDuration);
        }
      }),
      map(user => {
        return !!user;
      })
    );
  }

  private autoLogout(duration: number) {
    if (this.activeLogoutTimer) {
      clearTimeout(this.activeLogoutTimer);
    }
    this.activeLogoutTimer = setTimeout(() => {
      console.log("logging out...");
      this.logout();
    }, duration);
  }

  setUserData(userData: AuthResponseData) {
    const user = new User(
      userData.id,
      userData.name,
      userData.email,
      userData.expirationDate,
      "Bearer " + userData.token
    );
    this._user.next(user);
    this.autoLogout(user.tokenDuration);
    this.storeAuthData(userData.id, userData.name, userData.email, userData.expirationDate, userData.token);
  }

  private storeAuthData(userId: number, name: string, email: string, expirationDate: number, token: string) {
    const data = JSON.stringify({
      id: userId,
      name: name,
      email: email,
      expirationDate: expirationDate,
      token: "Bearer " + token
    });
    this.storageService.setData('authData', data);
    localStorage.setItem("token", "Bearer " + token);
  }
}
