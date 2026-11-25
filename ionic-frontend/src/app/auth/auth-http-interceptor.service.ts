import { HttpErrorResponse, HttpEvent, HttpHandler, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { environment } from 'src/environments/environment';
import { ERROR_FORMAT_HEADER, ERROR_FORMAT_JSON } from '../service/api-error';
import { AuthService } from './auth.service';

/**
 * Setzt Sprache und `X-Error-Format` auf jeden Request. Geschützte Backend-Requests (`/api/**` außer `/api/auth/**`
 * sowie `/actuator/health`) bekommen bei bestehender Session zusätzlich Access Token und DeviceId (DIV-65):
 *
 * - Vorab wird ein gültiger Access Token geholt (notfalls per gemeinsamem Refresh). Scheitert der Refresh
 *   vorübergehend (Netz, Server), scheitert der Request mit diesem Fehler; die Session bleibt.
 * - Antwortet der Server mit 401, in dieser Reihenfolge:
 *   1. Hat sich seit dem Senden die Anmeldung geändert (Logout, Session-Ende, neuer Login), wird der Fehler
 *      durchgereicht – kein Retry, kein Refresh: ein Request wird nie unter einer anderen Anmeldung wiederholt.
 *   2. Gilt inzwischen ein anderer Access Token (anderer Request hat erneuert), einmal damit wiederholen.
 *   3. Sonst einmal erneuern und wiederholen. Eine weitere 401 wird durchgereicht (keine Schleife).
 * - Requests an `/api/auth/**` lösen nie einen Refresh aus.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthHttpInterceptorService {

  private readonly apiUrl = `${environment.apiBaseUrl}/api/`;
  private readonly authUrl = `${environment.apiBaseUrl}/api/auth/`;
  private readonly healthUrl = `${environment.apiBaseUrl}/actuator/health`;

  constructor(
    private authService: AuthService,
    private translate: TranslateService
  ) { }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // ohne Session (z. B. Health-Check auf der Anmeldeseite) wie bisher ohne Token
    if (!this.needsToken(request.url) || !this.authService.hasSession()) {
      return next.handle(this.addCommonHeaders(request));
    }
    const generation = this.authService.authGeneration;
    return this.authService.getValidAccessToken().pipe(
      switchMap(token => this.sendWithToken(request, next, token).pipe(
        catchError(err => this.handleUnauthorized(err, request, next, token, generation))
      ))
    );
  }

  private handleUnauthorized(err: unknown, request: HttpRequest<any>, next: HttpHandler, sentToken: string,
                             generation: number): Observable<HttpEvent<any>> {
    if (!(err instanceof HttpErrorResponse) || err.status !== 401
        || this.authService.authGeneration !== generation) {
      return throwError(() => err);
    }
    const current = this.authService.currentAccessToken();
    if (current && current !== sentToken) {
      return this.sendWithToken(request, next, current);
    }
    return this.authService.refresh().pipe(
      switchMap(token => this.authService.authGeneration === generation
        ? this.sendWithToken(request, next, token)
        : throwError(() => err))
    );
  }

  private sendWithToken(request: HttpRequest<any>, next: HttpHandler, token: string): Observable<HttpEvent<any>> {
    // nur Requests mit Token warten auf die DeviceId (DIV-46)
    return this.authService.getDeviceId().pipe(
      switchMap(deviceId => next.handle(this.addCommonHeaders(request.clone({
        setHeaders: { Authorization: `Bearer ${token}`, DeviceId: deviceId }
      }))))
    );
  }

  private needsToken(url: string): boolean {
    return (url.startsWith(this.apiUrl) && !url.startsWith(this.authUrl)) || url.startsWith(this.healthUrl);
  }

  private addCommonHeaders(request: HttpRequest<any>) {
    const currentLang = this.translate.getCurrentLang() || 'de';
    return request.clone({
      setHeaders: {
        'Accept-Language': currentLang,
        // auch ohne Token: Login, Registrierung und Passwort-Reset werten Fehlercodes aus (DIV-108)
        [ERROR_FORMAT_HEADER]: ERROR_FORMAT_JSON
      }
    });
  }
}
