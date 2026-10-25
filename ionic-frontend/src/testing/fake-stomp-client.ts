import { Client, IFrame, IMessage, StompConfig, StompHeaders } from '@stomp/stompjs';

export interface FakeStompSubscription {
  topic: string;
  callback: (message: IMessage) => void;
  unsubscribe: jasmine.Spy;
  /** false nach `unsubscribe()` oder Ende der Sitzung (`simulateClose()`). */
  alive: boolean;
}

/**
 * Ersatz für den STOMP-Client im `WebSocketService` (über `STOMP_CLIENT_FACTORY`). Hält die übergebene Konfiguration
 * und spielt die Callbacks des echten Clients nach: `runBeforeConnect()`, `simulateConnect()`, `simulateClose()`,
 * `simulateStompError()`, `deliver(topic, body)`. Wie beim echten Client erlöschen alle Subscriptions mit dem Ende der
 * Sitzung (`simulateClose()`). `deactivate()` liefert `deactivation` – im Test durch eine offene
 * Promise ersetzbar, um eine noch laufende Deaktivierung nachzustellen.
 */
export class FakeStompClient {
  active = false;
  connectHeaders: StompHeaders = {};
  subscriptions: FakeStompSubscription[] = [];
  deactivation: Promise<void> = Promise.resolve();

  activate = jasmine.createSpy('activate').and.callFake(() => { this.active = true; });
  deactivate = jasmine.createSpy('deactivate').and.callFake(() => {
    this.active = false;
    return this.deactivation;
  });
  subscribe = jasmine.createSpy('subscribe').and.callFake((topic: string, callback: (message: IMessage) => void) => {
    const subscription: FakeStompSubscription = {
      topic, callback, alive: true,
      unsubscribe: jasmine.createSpy('unsubscribe').and.callFake(() => { subscription.alive = false; }),
    };
    this.subscriptions.push(subscription);
    return subscription;
  });

  constructor(public config: StompConfig) {}

  async runBeforeConnect(): Promise<void> {
    await this.config.beforeConnect?.(this as unknown as Client);
  }

  simulateConnect() {
    this.config.onConnect?.({} as IFrame);
  }

  simulateClose() {
    this.subscriptions.forEach(subscription => subscription.alive = false);
    this.config.onWebSocketClose?.({} as CloseEvent);
  }

  simulateStompError() {
    this.config.onStompError?.({ headers: { message: 'Unauthorized' } } as unknown as IFrame);
  }

  /** Stellt eine Nachricht an alle lebenden Subscriptions des Topics zu. */
  deliver(topic: string, body: string) {
    this.aliveSubscriptions(topic).forEach(subscription => subscription.callback({ body } as IMessage));
  }

  aliveSubscriptions(topic: string): FakeStompSubscription[] {
    return this.subscriptions.filter(subscription => subscription.topic === topic && subscription.alive);
  }
}
