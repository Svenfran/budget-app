import { HttpRequest, HttpHandler } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { take, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class AuthHttpInterceptorService {

  constructor(
    private authService: AuthService,
    private translate: TranslateService
  ) { }

  intercept(request: HttpRequest<any>, next: HttpHandler) {
    // Token sofort lesen (logout() setzt den Nutzer direkt nach dem Logout-Request zurück);
    // nur Requests mit Token warten auf die DeviceId (DIV-46), alle anderen gehen sofort raus.
    return this.authService.token.pipe(
      take(1),
      switchMap(token => {
        if (!token) {
          return next.handle(this.addLanguage(request));
        }
        return this.authService.getDeviceId().pipe(
          switchMap(deviceId => next.handle(this.addToken(request, token, deviceId)))
        );
      })
    )
  }


  private addToken(request: HttpRequest<any>, token: string, deviceId: string) {
    const currentLang = this.translate.getCurrentLang() || 'de';
    return request.clone({
      setHeaders: {
        Authorization: token,
        DeviceId: deviceId,
        'Accept-Language': currentLang
      }
    });
  }

  private addLanguage(request: HttpRequest<any>) {
    const currentLang = this.translate.getCurrentLang() || 'de';
    return request.clone({
      setHeaders: {
        'Accept-Language': currentLang
      }
    });
  }
}
