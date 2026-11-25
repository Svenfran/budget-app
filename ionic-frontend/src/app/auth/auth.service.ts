import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, firstValueFrom, from, of, throwError } from 'rxjs';
import { catchError, finalize, map, shareReplay, switchMap, tap, timeout } from 'rxjs/operators';
import { environment } from 'src/environments/environment';
import { INIT_NUMBERS } from '../constants/default-values';
import { StorageService } from '../service/storage.service';
import type { WsAuthResult } from '../service/websocket.service';
import { DEVICE_ID } from './device-id';
import { SessionStore, withinBudget } from './session-store';
import { StoredSession, TOKEN_STORAGE, TokenStorage } from './token-storage';
import { User } from './user';

/** Antwort von Login, Registrierung und Refresh (backend `AuthenticationResponse`). */
export interface AuthResponseData {
  id: number,
  name: string,
  email: string,
  /** Ablauf des Access Tokens (ms seit Epoch). */
  expirationDate: number,
  /** Access Token (JWT, ohne "Bearer "). */
  token: string,
  refreshToken: string,
  /** Absolute Höchstdauer der Session (ms seit Epoch). */
  sessionExpirationDate: number,
}

/** Es gibt keine Session (nicht angemeldet). */
export class NoSessionError extends Error {
  constructor() {
    super('Keine Session');
    this.name = 'NoSessionError';
  }
}

/** Die Session wurde beendet (Refresh abgelehnt) oder gewechselt (Logout, neuer Login), während der Vorgang lief. */
export class SessionEndedError extends Error {
  constructor() {
    super('Session beendet');
    this.name = 'SessionEndedError';
  }
}

/** Legacy-Anmeldedaten vor DIV-65 (unverschlüsselt); werden beim ersten Start bzw. Login entfernt (E2). */
const LEGACY_AUTH_DATA_KEY = 'authData';
const LEGACY_TOKEN_KEY = 'token';

interface RefreshOperation {
  shared: Observable<string>;
  generation: number;
}

/**
 * Anmeldung mit Refresh-Token-Session (DIV-65).
 *
 * - **Arbeitsspeicher ist maßgeblich:** Session (Refresh Token, Profil) und Access Token liegen im Speicher; der
 *   Access Token wird nie gespeichert und nicht über {@link user} verteilt (ein Refresh soll keine Neuladungen
 *   auslösen). Der {@link TokenStorage} wird über eine serielle Warteschlange ({@link SessionStore}) nachgeführt.
 * - **Auth-Generation:** zählt bei jedem Session-Start, Logout und Session-Ende hoch. Asynchrone Ergebnisse (Refresh,
 *   Wiederherstellung) werden verworfen, wenn sich die Generation inzwischen geändert hat.
 * - **Refresh:** höchstens einer gleichzeitig (geteilt), mit eigenem Timeout. 401 beendet die Session (Navigation
 *   zur Anmeldung) – außer inzwischen gilt ein neuerer Refresh Token. Netzwerk-/Server-Fehler lassen die Session bestehen.
 * - **Logout:** lokal sofort, der Server-Widerruf läuft im Hintergrund.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private _user = new BehaviorSubject<User | null>(null);
  private apiBaseUrl = environment.apiBaseUrl;
  private authUrl = `${this.apiBaseUrl}/api/auth/authenticate`;
  private registerUrl = `${this.apiBaseUrl}/api/auth/register`;
  private refreshUrl = `${this.apiBaseUrl}/api/auth/refresh`;
  private logoutUrl = `${this.apiBaseUrl}/api/auth/logout`;

  private session: StoredSession | null = null;
  private accessToken: string | null = null;
  private accessTokenExpiresAt = 0;
  private generation = 0;
  private refreshOperation: RefreshOperation | null = null;
  /** Wiederherstellung aus dem Speicher, höchstens einmal pro App-Laufzeit (geteilt). */
  private restore: Promise<void> | null = null;
  private legacyRemoved = false;
  private readonly store: SessionStore;

  constructor(
    private http: HttpClient,
    private storageService: StorageService,
    private router: Router,
    @Inject(DEVICE_ID) private deviceIdReady: Promise<string>,
    @Inject(TOKEN_STORAGE) tokenStorage: TokenStorage,
  ) {
    this.store = new SessionStore(tokenStorage, g => g === this.generation, () => this.session && { ...this.session });
  }

  get user(): Observable<User | null> {
    return this._user.asObservable();
  }

  get userIsAuthenticated(): Observable<boolean> {
    return this._user.asObservable().pipe(map(user => !!user));
  }

  /** Aktuelle Auth-Generation (für Aufrufer, die verspätete Antworten erkennen müssen, z. B. den Interceptor). */
  get authGeneration(): number {
    return this.generation;
  }

  /** Gibt es eine Session (angemeldet oder wiederhergestellt)? */
  hasSession(): boolean {
    return this.session !== null;
  }

  /** Der zuletzt ausgestellte Access Token (auch abgelaufen) oder `null`. */
  currentAccessToken(): string | null {
    return this.accessToken;
  }

  // Wartet, bis Device.getId() geantwortet hat – nie eine noch leere DeviceId senden (DIV-46)
  getDeviceId(): Observable<string> {
    return from(this.deviceIdReady);
  }

  login(userEmail: string, password: string): Observable<AuthResponseData> {
    return this.getDeviceId().pipe(
      switchMap(deviceId => this.http.post<AuthResponseData>(this.authUrl,
        { email: userEmail, password: password, deviceId: deviceId })),
      tap(response => this.startSession(response)));
  }

  register(userName: string, userEmail: string, password: string): Observable<AuthResponseData> {
    return this.getDeviceId().pipe(
      switchMap(deviceId => this.http.post<AuthResponseData>(this.registerUrl,
        { name: userName, email: userEmail, password: password, deviceId: deviceId })),
      tap(response => this.startSession(response)));
  }

  /**
   * Stellt die Session beim App-Start aus dem Speicher wieder her (ohne Access Token, der kommt beim ersten Request).
   * Liest den Speicher höchstens einmal pro App-Laufzeit; nach Logout/Session-Ende nicht erneut. Liefert, ob danach
   * ein Nutzer angemeldet ist.
   */
  autoLogin(): Observable<boolean> {
    if (this._user.value) {
      return of(true);
    }
    this.restore ??= this.restoreSession();
    return from(this.restore).pipe(map(() => !!this._user.value));
  }

  /** Gültiger Access Token (Restlaufzeit mindestens `minValidityMs`), notfalls per Refresh. */
  getValidAccessToken(minValidityMs: number = INIT_NUMBERS.ACCESS_TOKEN_MIN_VALIDITY_MS): Observable<string> {
    if (!this.session) {
      return throwError(() => new NoSessionError());
    }
    if (this.accessToken && this.accessTokenExpiresAt - Date.now() > minValidityMs) {
      return of(this.accessToken);
    }
    return this.refresh();
  }

  /**
   * Erneuert das Token-Paar. Läuft schon ein Refresh derselben Generation, teilen sich alle Aufrufer dessen Ergebnis.
   * Fehler: {@link NoSessionError} ohne Session, {@link SessionEndedError} bei Ablehnung (401; Session lokal beendet)
   * oder Generationswechsel, sonst der HTTP-/Timeout-Fehler (Session bleibt).
   */
  refresh(): Observable<string> {
    if (!this.session) {
      return throwError(() => new NoSessionError());
    }
    if (this.refreshOperation?.generation === this.generation) {
      return this.refreshOperation.shared;
    }
    const generation = this.generation;
    const sentRefreshToken = this.session.refreshToken;
    const operation: RefreshOperation = { generation, shared: undefined! };
    operation.shared = this.getDeviceId().pipe(
      switchMap(deviceId => this.http.post<AuthResponseData>(this.refreshUrl,
        { refreshToken: sentRefreshToken, deviceId }).pipe(timeout(INIT_NUMBERS.REFRESH_TIMEOUT_MS))),
      catchError(err => this.refreshFailed(err, generation, sentRefreshToken)),
      switchMap(response => response ? from(this.refreshSucceeded(response, generation)) : this.currentTokenOf(generation)),
      finalize(() => {
        if (this.refreshOperation === operation) {
          this.refreshOperation = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.refreshOperation = operation;
    return operation.shared;
  }

  /**
   * Zugangsdaten für den WebSocket-CONNECT: wirft nie. `token` (mit "Bearer "), `no-session` ohne bzw. nach beendeter
   * Session, `retry` bei vorübergehendem Refresh-Fehler (Netz, Server, Timeout).
   */
  webSocketAuthorization(): Promise<WsAuthResult> {
    return firstValueFrom(this.getValidAccessToken(INIT_NUMBERS.WEBSOCKET_TOKEN_MIN_VALIDITY_MS)).then(
      (token): WsAuthResult => ({ kind: 'token', value: `Bearer ${token}` }),
      (err): WsAuthResult => err instanceof NoSessionError || err instanceof SessionEndedError
        ? { kind: 'no-session' } : { kind: 'retry' });
  }

  /**
   * Meldet lokal sofort ab (Zustand und Speicher), widerruft die Session danach im Hintergrund am Server. Navigiert
   * nicht selbst – manuelle Logout-Pfade navigieren nach `/auth` (DIV-57).
   */
  logout(): void {
    const refreshToken = this.session?.refreshToken;
    const accessToken = this.accessToken;
    this.clearLocalSession();
    const headers: Record<string, string> = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
    this.http.post<void>(this.logoutUrl, refreshToken ? { refreshToken } : null, { headers })
      .subscribe({ error: () => undefined });
  }

  /** Neuer Name im Profil der Session (z. B. nach Namensänderung); ändert keine Tokens. */
  updateUserName(name: string): void {
    if (!this.session) {
      return;
    }
    this.session = { ...this.session, name };
    this._user.next(this.toUser(this.session));
    this.store.save(this.generation);
  }

  private startSession(response: AuthResponseData) {
    this.generation++;
    this.restore ??= Promise.resolve();
    this.refreshOperation = null;
    this.removeLegacyAuthData();
    this.session = {
      refreshToken: response.refreshToken,
      userId: response.id,
      name: response.name,
      email: response.email,
      sessionExpiresAt: response.sessionExpirationDate,
    };
    this.setAccessToken(response);
    this._user.next(this.toUser(this.session));
    // Login wartet nicht auf den Speicher
    this.store.save(this.generation);
  }

  private async restoreSession(): Promise<void> {
    const generation = this.generation;
    this.removeLegacyAuthData();
    const loaded = await withinBudget(this.store.load(), INIT_NUMBERS.STORAGE_WAIT_MS);
    if (generation !== this.generation || !loaded.done || !loaded.value) {
      // inzwischen Login/Logout, Speicher zu langsam oder keine Session: späte Leseantwort wird verworfen
      return;
    }
    if (loaded.value.sessionExpiresAt <= Date.now()) {
      this.store.clear();
      return;
    }
    this.session = loaded.value;
    this._user.next(this.toUser(this.session));
  }

  private async refreshSucceeded(response: AuthResponseData, generation: number): Promise<string> {
    if (generation !== this.generation || !this.session) {
      throw new SessionEndedError();
    }
    this.session = {
      ...this.session,
      refreshToken: response.refreshToken,
      sessionExpiresAt: response.sessionExpirationDate,
    };
    this.setAccessToken(response);
    // Neues Paar möglichst gespeichert haben, bevor der Token verteilt wird; höchstens STORAGE_WAIT_MS ab Einreihen
    await withinBudget(this.store.save(generation), INIT_NUMBERS.STORAGE_WAIT_MS);
    if (generation !== this.generation) {
      throw new SessionEndedError();
    }
    return response.token;
  }

  /** Fehler beim Refresh; `of(null)` = ignorieren, weil inzwischen ein neuerer Refresh Token gilt. */
  private refreshFailed(err: unknown, generation: number, sentRefreshToken: string): Observable<null> {
    if (generation !== this.generation) {
      return throwError(() => new SessionEndedError());
    }
    if (err instanceof HttpErrorResponse && err.status === 401) {
      if (this.session && this.session.refreshToken !== sentRefreshToken) {
        return of(null);
      }
      this.endSession();
      return throwError(() => new SessionEndedError());
    }
    // Netzwerk, Timeout, 5xx, 429: vorübergehend, Session bleibt
    return throwError(() => err);
  }

  private currentTokenOf(generation: number): Observable<string> {
    return generation === this.generation && this.accessToken
      ? of(this.accessToken)
      : throwError(() => new SessionEndedError());
  }

  /** Session vom Server abgelehnt: lokal beenden und zur Anmeldung. */
  private endSession() {
    this.clearLocalSession();
    this.router.navigateByUrl('/auth', { replaceUrl: true });
  }

  private clearLocalSession() {
    this.generation++;
    this.restore ??= Promise.resolve();
    this.refreshOperation = null;
    this.session = null;
    this.accessToken = null;
    this.accessTokenExpiresAt = 0;
    this._user.next(null);
    this.store.clear();
    this.storageService.removeData('ACTIVE_GROUP');
  }

  private setAccessToken(response: AuthResponseData) {
    this.accessToken = response.token;
    this.accessTokenExpiresAt = response.expirationDate;
  }

  private toUser(session: StoredSession): User {
    return new User(session.userId, session.name, session.email, session.sessionExpiresAt);
  }

  /** Entfernt einmalig die unverschlüsselten Anmeldedaten älterer App-Versionen (E2). */
  private removeLegacyAuthData() {
    if (this.legacyRemoved) {
      return;
    }
    this.legacyRemoved = true;
    localStorage.removeItem(LEGACY_TOKEN_KEY);
    this.storageService.removeData(LEGACY_AUTH_DATA_KEY).catch(() => undefined);
  }
}
