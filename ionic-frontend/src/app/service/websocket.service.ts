import { Inject, Injectable, InjectionToken } from '@angular/core';
import { Client, IFrame, IMessage, StompConfig, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';
import { environment } from 'src/environments/environment';
import { DEVICE_ID } from '../auth/device-id';
import { GroupService } from './group.service';
import { HealthCheckService } from './healthcheck.service';

export type StompClientFactory = (config: StompConfig) => Client;

/** Erzeugt den STOMP-Client (SockJS). Als Token, damit Tests einen Fake-Client einsetzen können (wie `DEVICE_ID`). */
export const STOMP_CLIENT_FACTORY = new InjectionToken<StompClientFactory>('STOMP_CLIENT_FACTORY', {
  providedIn: 'root',
  factory: () => (config: StompConfig) => new Client({
    ...config,
    webSocketFactory: () => new SockJS(`${environment.websocketUrl}`, {
      transports: ["websocket"],
    }),
  }),
});

/** Liefert den fertigen Authorization-Wert (`"Bearer <jwt>"`, wie `User.token`) oder nichts, wenn keiner vorliegt. */
export type AuthorizationProvider = () => string | null | undefined;

export interface SubscribeOptions {
  /** Wer das Topic registriert hat; `unsubscribeOwner(owner)` entfernt nur dessen Topics. */
  owner: string;
  /** Nachrichten verwerfen, deren `groupId` (bei Arrays: des ersten Elements) nicht die aktive Gruppe ist. */
  filterByActiveGroup?: boolean;
}

interface Registration {
  topic: string;
  handler: (data: any) => void;
  options: SubscribeOptions;
  stompSubscription?: StompSubscription;
}

/**
 * Eine STOMP-Verbindung pro angemeldetem Nutzer mit eigener Subscription-Registry (DIV-36):
 * - `connect()` erzeugt höchstens einen aktiven Client; vor jedem (Re-)Connect werden `Authorization` und `DeviceId`
 *   frisch gesetzt. Ein neuer Client wird erst aktiviert, wenn die Deaktivierung des vorherigen abgeschlossen ist;
 *   bei mehreren Aufrufen in dieser Zeit gilt nur der letzte (`connect` oder `disconnect`).
 * - Registrierte Topics werden bei jedem `onConnect` neu abonniert (auch nach Reconnect); danach meldet
 *   `reconnected$` jedes Wiederverbinden (nicht den ersten Connect).
 * - Die Registry überdauert `disconnect()`: Topics enthalten keine userId, Handler sind nutzerunabhängig, und eine von
 *   Ionic gecachte Seite (kein erneutes `ngOnInit`) behält so ihre Topics über Logout/Login. Dass ein neuer Nutzer keine
 *   Events des vorherigen erhält, sichert die neue Verbindung mit seinem Token (Principal = userId).
 * - Nachrichten werden zentral geparst, optional nach aktiver Gruppe gefiltert; Fehler im Handler werden geloggt
 *   und brechen die weitere Verarbeitung nicht ab.
 * - Verspätete Callbacks eines alten, noch deaktivierenden Clients werden ignoriert.
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client: Client | null = null;
  private connected = false;
  private hasConnectedBefore = false;
  private registrations = new Map<string, Registration>();
  private pendingDeactivation: Promise<void> = Promise.resolve();
  private latestRequest = 0;
  private reconnectedSubject = new Subject<void>();
  readonly reconnected$ = this.reconnectedSubject.asObservable();

  constructor(
    private groupService: GroupService,
    private healthService: HealthCheckService,
    @Inject(STOMP_CLIENT_FACTORY) private createClient: StompClientFactory,
    @Inject(DEVICE_ID) private deviceId: Promise<string>,
  ) {}

  async connect(authorization: AuthorizationProvider): Promise<void> {
    if (this.client?.active) {
      return;
    }
    const request = ++this.latestRequest;
    await this.pendingDeactivation;
    if (request !== this.latestRequest || this.client) {
      return; // inzwischen überholt durch einen neueren connect()/disconnect()
    }

    const client: Client = this.createClient({
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
      beforeConnect: async () => {
        const authorizationValue = authorization();
        const deviceId = await this.deviceId;
        if (!authorizationValue) {
          this.deactivate(client);
          return;
        }
        client.connectHeaders = { Authorization: authorizationValue, DeviceId: deviceId };
      },
      onConnect: () => this.onConnect(client),
      onStompError: (frame: IFrame) => {
        if (client !== this.client) return;
        if (this.connected) {
          // bestehende Sitzung, z. B. vom Server wegen ausgebliebener Heartbeats beendet ("Session closed."):
          // der Client verbindet sich selbst neu
          console.warn('STOMP-Fehler, Verbindung wird neu aufgebaut:', frame.headers['message']);
          return;
        }
        // Fehler beim Verbindungsaufbau = abgelehnte Anmeldung: nicht endlos neu verbinden (kein App-Logout, D-002)
        console.warn('STOMP-Anmeldung abgelehnt, WebSocket wird deaktiviert:', frame.headers['message']);
        this.deactivate(client);
      },
      onWebSocketClose: () => this.onWebSocketClose(client),
    });
    this.client = client;
    this.connected = false;
    this.hasConnectedBefore = false;
    client.activate();
  }

  /**
   * Beendet Subscriptions und Verbindung (Logout, Nutzerwechsel). Die registrierten Topics bleiben erhalten und werden
   * beim nächsten `connect()` neu abonniert; entfernt werden sie nur über `unsubscribeOwner()`.
   */
  disconnect(): Promise<void> {
    this.latestRequest++;
    this.registrations.forEach(registration => this.unsubscribeStomp(registration));
    if (this.client) {
      this.deactivate(this.client);
    }
    return this.pendingDeactivation;
  }

  isConnected(): boolean {
    return this.connected;
  }

  subscribe<T>(topic: string, handler: (data: T) => void, options: SubscribeOptions) {
    const existing = this.registrations.get(topic);
    if (existing) {
      this.unsubscribeStomp(existing);
    }
    const registration: Registration = { topic, handler, options };
    this.registrations.set(topic, registration);
    if (this.connected) {
      this.subscribeStomp(this.client!, registration);
    }
  }

  unsubscribeOwner(owner: string) {
    this.registrations.forEach((registration, topic) => {
      if (registration.options.owner === owner) {
        this.unsubscribeStomp(registration);
        this.registrations.delete(topic);
      }
    });
  }

  private onConnect(client: Client) {
    if (client !== this.client) return;
    this.connected = true;
    this.registrations.forEach(registration => this.subscribeStomp(client, registration));
    if (this.hasConnectedBefore) {
      this.reconnectedSubject.next();
    }
    this.hasConnectedBefore = true;
  }

  private onWebSocketClose(client: Client) {
    if (client !== this.client) return;
    const wasConnected = this.connected;
    this.connected = false;
    if (wasConnected) {
      // nur Status aktualisieren, keine Navigation (künftige Offline-Fähigkeit der Einkaufslisten)
      this.healthService.getHealthStatus();
    }
  }

  private deactivate(client: Client) {
    if (client === this.client) {
      this.client = null;
      this.connected = false;
    }
    const deactivation = client.deactivate();
    this.pendingDeactivation = Promise.all([this.pendingDeactivation, deactivation]).then(() => undefined, () => undefined);
  }

  private subscribeStomp(client: Client, registration: Registration) {
    // die Subscription einer früheren Sitzung ist mit der Verbindung erloschen und wird nur ersetzt
    const subscription = client.subscribe(registration.topic, message => {
      if (client !== this.client || this.registrations.get(registration.topic) !== registration
          || registration.stompSubscription !== subscription) return;
      this.handleMessage(registration, message);
    });
    registration.stompSubscription = subscription;
  }

  private unsubscribeStomp(registration: Registration) {
    if (this.connected && registration.stompSubscription) {
      try {
        registration.stompSubscription.unsubscribe();
      } catch (err) {
        console.warn(`Abmelden von ${registration.topic} fehlgeschlagen:`, err);
      }
    }
    registration.stompSubscription = undefined;
  }

  private handleMessage(registration: Registration, message: IMessage) {
    try {
      const data = JSON.parse(message.body);
      if (registration.options.filterByActiveGroup && !this.belongsToActiveGroup(data)) {
        return;
      }
      registration.handler(data);
    } catch (err) {
      console.error(`WebSocket-Nachricht auf ${registration.topic} konnte nicht verarbeitet werden:`, err);
    }
  }

  private belongsToActiveGroup(data: any): boolean {
    const first = Array.isArray(data) ? data[0] : data;
    return first?.groupId === this.groupService.activeGroup().id;
  }
}
