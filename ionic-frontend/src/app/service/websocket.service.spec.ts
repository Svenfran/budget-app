import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { fakeAsync, flushMicrotasks, TestBed } from '@angular/core/testing';
import { Client, StompConfig } from '@stomp/stompjs';
import { environment } from 'src/environments/environment';
import { FakeStompClient } from 'src/testing/fake-stomp-client';
import { aGroup, aLoggedInUser, REFERENCE_DATE } from 'src/testing/test-data';
import { provideServiceTesting } from 'src/testing/test-providers';
import { DEVICE_ID } from '../auth/device-id';
import { GroupService } from './group.service';
import { AuthorizationProvider, STOMP_CLIENT_FACTORY, WebSocketService } from './websocket.service';

describe('WebSocketService', () => {
  let service: WebSocketService;
  let http: HttpTestingController;
  let clients: FakeStompClient[];
  const activeGroup = signal(aGroup({ id: 1 }));
  const healthUrl = `${environment.apiBaseUrl}/actuator/health`;
  const topic = '/user/notification/add-item';
  const bearer: AuthorizationProvider = () => 'Bearer test-jwt';

  /** connect() bis einschließlich beforeConnect durchlaufen lassen; liefert den neu erzeugten Client. */
  function connect(authorization: AuthorizationProvider = bearer): FakeStompClient {
    const count = clients.length;
    service.connect(authorization);
    flushMicrotasks();
    expect(clients.length).withContext('neuer Client erzeugt').toBe(count + 1);
    const client = clients[clients.length - 1];
    client.runBeforeConnect();
    flushMicrotasks();
    return client;
  }

  function connected(authorization: AuthorizationProvider = bearer): FakeStompClient {
    const client = connect(authorization);
    client.simulateConnect();
    return client;
  }

  /** Eine Deaktivierung, die erst auf `resolve()` hin abschließt (alter WebSocket noch offen). */
  function openDeactivation(client: FakeStompClient): () => void {
    let resolve!: () => void;
    client.deactivation = new Promise<void>(r => resolve = r);
    return () => resolve();
  }

  beforeEach(() => {
    clients = [];
    activeGroup.set(aGroup({ id: 1 }));
    TestBed.configureTestingModule({
      providers: [
        ...provideServiceTesting(),
        { provide: GroupService, useValue: { activeGroup } },
        { provide: DEVICE_ID, useValue: Promise.resolve('device-1') },
        {
          provide: STOMP_CLIENT_FACTORY,
          useValue: (config: StompConfig) => {
            const client = new FakeStompClient(config);
            clients.push(client);
            return client as unknown as Client;
          },
        },
      ],
    });
    service = TestBed.inject(WebSocketService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('Verbindung', () => {
    it('connect erzeugt genau einen aktiven Client; ein weiterer connect bei aktivem Client erzeugt keinen zweiten', fakeAsync(() => {
      const client = connect();

      service.connect(bearer);
      flushMicrotasks();

      expect(clients.length).toBe(1);
      expect(client.activate).toHaveBeenCalledTimes(1);
    }));

    it('setzt Authorization unverändert aus dem Provider (kein doppeltes "Bearer ") und die DeviceId', fakeAsync(() => {
      jasmine.clock().mockDate(REFERENCE_DATE);
      try {
        const user = aLoggedInUser({ token: 'Bearer test-jwt' });

        const client = connect(() => user.token);

        expect(client.connectHeaders).toEqual({ Authorization: 'Bearer test-jwt', DeviceId: 'device-1' });
      } finally {
        jasmine.clock().uninstall();
      }
    }));

    it('liest das Token vor jedem (Re-)Connect neu', fakeAsync(() => {
      let token = 'Bearer alt';
      const client = connect(() => token);

      token = 'Bearer neu';
      client.runBeforeConnect();
      flushMicrotasks();

      expect(client.connectHeaders['Authorization']).toBe('Bearer neu');
    }));

    it('deaktiviert den Client, wenn der Provider kein Token liefert', fakeAsync(() => {
      const client = connect(() => undefined);

      expect(client.deactivate).toHaveBeenCalled();
      expect(client.active).toBeFalse();
      expect(client.connectHeaders).toEqual({});
    }));

    it('bleibt aktiv, wenn der Server eine bestehende Sitzung mit einem STOMP-Fehler beendet (Heartbeat-Timeout)', fakeAsync(() => {
      // Regression: Spring schickt nach 30 s ohne Client-Heartbeat (z. B. gedrosselter Hintergrund-Tab) ERROR
      // "Session closed." – der Client muss danach selbst neu verbinden, statt sich dauerhaft zu deaktivieren.
      spyOn(console, 'warn');
      const handler = jasmine.createSpy('handler');
      service.subscribe(topic, handler, { owner: 'shoppinglist' });
      const client = connected();

      client.simulateStompError('Session closed.');
      client.simulateClose();
      http.expectOne(healthUrl).flush({ status: 'UP' });
      client.runBeforeConnect();
      flushMicrotasks();
      client.simulateConnect();
      client.deliver(topic, JSON.stringify({ id: 7 }));

      expect(client.deactivate).not.toHaveBeenCalled();
      expect(client.active).toBeTrue();
      expect(service.isConnected()).toBeTrue();
      expect(handler).toHaveBeenCalledOnceWith({ id: 7 });
    }));

    it('deaktiviert den Client bei einem STOMP-Fehler beim Verbindungsaufbau (abgelehnte Anmeldung), ohne neu zu verbinden', fakeAsync(() => {
      spyOn(console, 'warn');
      const client = connect();

      client.simulateStompError();
      flushMicrotasks();

      expect(client.deactivate).toHaveBeenCalledTimes(1);
      expect(client.active).toBeFalse();
      expect(clients.length).toBe(1);
      expect(service.isConnected()).toBeFalse();
    }));

    it('isConnected folgt onConnect und dem Schließen des WebSockets', fakeAsync(() => {
      const client = connect();
      expect(service.isConnected()).toBeFalse();

      client.simulateConnect();
      expect(service.isConnected()).toBeTrue();

      client.simulateClose();
      expect(service.isConnected()).toBeFalse();
      http.expectOne(healthUrl).flush({ status: 'UP' });
    }));

    it('fragt nach Verbindungsverlust einmal den Health-Status per HTTP ab, ohne vorherige Verbindung nicht', fakeAsync(() => {
      const client = connect();
      client.simulateClose();
      http.expectNone(healthUrl);

      client.simulateConnect();
      client.simulateClose();

      http.expectOne(healthUrl).flush({ status: 'UP' });
    }));
  });

  describe('Trennen und Nutzerwechsel', () => {
    it('disconnect beendet Subscriptions und Client, meldet keine Verbindung mehr und behält die Registry für den nächsten Connect', fakeAsync(() => {
      // Regression: Eine von Ionic gecachte Shopping-Page registriert nach Logout/Login nicht erneut (kein ngOnInit);
      // ihre Topics müssen deshalb disconnect() überdauern.
      const handler = jasmine.createSpy('handler');
      service.subscribe(topic, handler, { owner: 'shoppinglist' });
      const client = connected();

      service.disconnect();
      flushMicrotasks();

      expect(client.deactivate).toHaveBeenCalled();
      expect(client.aliveSubscriptions(topic)).toEqual([]);
      expect(service.isConnected()).toBeFalse();

      const next = connected(() => 'Bearer anderer-nutzer');
      next.deliver(topic, JSON.stringify({ id: 7 }));

      expect(next.aliveSubscriptions(topic).length).toBe(1);
      expect(handler).toHaveBeenCalledOnceWith({ id: 7 });
    }));

    it('verspätete Callbacks des alten Clients nach einem Nutzerwechsel bleiben ohne Wirkung', fakeAsync(() => {
      const handler = jasmine.createSpy('handler');
      const reconnected = jasmine.createSpy('reconnected');
      service.reconnected$.subscribe(reconnected);
      service.subscribe(topic, handler, { owner: 'app' });
      const old = connected();
      const oldCallback = old.subscriptions[0].callback;

      service.disconnect();
      service.subscribe(topic, handler, { owner: 'app' });
      const current = connect(() => 'Bearer anderer-nutzer');

      old.simulateConnect();
      oldCallback({ body: JSON.stringify({ groupId: 1 }) } as any);
      old.simulateClose();

      expect(old.subscribe).toHaveBeenCalledTimes(1);
      expect(handler).not.toHaveBeenCalled();
      expect(reconnected).not.toHaveBeenCalled();
      expect(current.subscribe).not.toHaveBeenCalled();
      http.expectNone(healthUrl);
    }));

    it('aktiviert den neuen Client erst, wenn die Deaktivierung des alten abgeschlossen ist', fakeAsync(() => {
      const old = connected();
      const finishDeactivation = openDeactivation(old);

      service.disconnect();
      service.connect(bearer);
      flushMicrotasks();
      expect(clients.length).withContext('noch kein neuer Client').toBe(1);

      finishDeactivation();
      flushMicrotasks();

      expect(clients.length).toBe(2);
      expect(clients[1].activate).toHaveBeenCalledTimes(1);
    }));

    it('connect(A), disconnect, connect(B) während offener Deaktivierung: genau ein Client mit den Credentials von B', fakeAsync(() => {
      const old = connected();
      const finishDeactivation = openDeactivation(old);
      service.disconnect();

      service.connect(() => 'Bearer A');
      service.disconnect();
      service.connect(() => 'Bearer B');
      flushMicrotasks();
      finishDeactivation();
      flushMicrotasks();

      expect(clients.length).toBe(2);
      clients[1].runBeforeConnect();
      flushMicrotasks();
      expect(clients[1].connectHeaders['Authorization']).toBe('Bearer B');
    }));

    it('connect und danach disconnect während offener Deaktivierung: kein neuer Client', fakeAsync(() => {
      const old = connected();
      const finishDeactivation = openDeactivation(old);
      service.disconnect();

      service.connect(bearer);
      service.disconnect();
      finishDeactivation();
      flushMicrotasks();

      expect(clients.length).toBe(1);
    }));
  });

  describe('Subscription-Registry', () => {
    it('abonniert vor dem Connect registrierte Topics bei onConnect und nach einem Reconnect erneut, Handler einmal pro Nachricht', fakeAsync(() => {
      const handler = jasmine.createSpy('handler');
      service.subscribe(topic, handler, { owner: 'app' });
      const client = connect();
      expect(client.subscribe).not.toHaveBeenCalled();

      client.simulateConnect();
      client.simulateClose();
      http.expectOne(healthUrl).flush({ status: 'UP' });
      client.simulateConnect();
      client.deliver(topic, JSON.stringify({ id: 7, groupId: 1 }));

      expect(client.subscribe).toHaveBeenCalledTimes(2);
      expect(client.aliveSubscriptions(topic).length).toBe(1);
      expect(handler).toHaveBeenCalledOnceWith({ id: 7, groupId: 1 });
    }));

    it('ein zweites subscribe desselben Topics beendet die alte Subscription; nur der neue Handler läuft, einmal', fakeAsync(() => {
      const first = jasmine.createSpy('first');
      const second = jasmine.createSpy('second');
      const client = connected();

      service.subscribe(topic, first, { owner: 'shoppinglist' });
      service.subscribe(topic, second, { owner: 'shoppinglist' });
      client.deliver(topic, JSON.stringify({ id: 7 }));

      expect(client.subscriptions[0].unsubscribe).toHaveBeenCalled();
      expect(first).not.toHaveBeenCalled();
      expect(second).toHaveBeenCalledTimes(1);
    }));

    it('unsubscribeOwner entfernt nur die Topics dieses Owners', fakeAsync(() => {
      const appHandler = jasmine.createSpy('app');
      const listHandler = jasmine.createSpy('shoppinglist');
      const client = connected();
      service.subscribe('/user/notification/update-group', appHandler, { owner: 'app' });
      service.subscribe(topic, listHandler, { owner: 'shoppinglist' });

      service.unsubscribeOwner('shoppinglist');
      client.deliver('/user/notification/update-group', JSON.stringify({ id: 1 }));
      client.deliver(topic, JSON.stringify({ id: 7 }));

      expect(appHandler).toHaveBeenCalledTimes(1);
      expect(listHandler).not.toHaveBeenCalled();
    }));

    it('reconnected$ meldet nicht den ersten Connect, aber jedes Wiederverbinden', fakeAsync(() => {
      const reconnected = jasmine.createSpy('reconnected');
      service.reconnected$.subscribe(reconnected);

      const client = connected();
      expect(reconnected).not.toHaveBeenCalled();

      client.simulateClose();
      http.expectOne(healthUrl).flush({ status: 'UP' });
      client.simulateConnect();

      expect(reconnected).toHaveBeenCalledTimes(1);
    }));
  });

  describe('Nachrichtenverarbeitung', () => {
    it('filterByActiveGroup lässt nur Nachrichten der aktiven Gruppe durch, ein leeres Array wird ohne Fehler verworfen', fakeAsync(() => {
      const handler = jasmine.createSpy('handler');
      spyOn(console, 'error');
      const client = connected();
      service.subscribe(topic, handler, { owner: 'shoppinglist', filterByActiveGroup: true });

      client.deliver(topic, JSON.stringify({ id: 1, groupId: 2 }));
      client.deliver(topic, JSON.stringify([]));
      client.deliver(topic, JSON.stringify([{ id: 2, groupId: 1 }]));
      client.deliver(topic, JSON.stringify({ id: 3, groupId: 1 }));

      expect(handler.calls.allArgs()).toEqual([[[{ id: 2, groupId: 1 }]], [{ id: 3, groupId: 1 }]]);
      expect(console.error).not.toHaveBeenCalled();
    }));

    it('ungültiges JSON oder ein werfender Handler wird geloggt, die nächste Nachricht wird verarbeitet', fakeAsync(() => {
      spyOn(console, 'error');
      const handler = jasmine.createSpy('handler').and.callFake((data: { id: number }) => {
        if (data.id === 1) throw new TypeError('veralteter State');
      });
      const client = connected();
      service.subscribe(topic, handler, { owner: 'app' });

      client.deliver(topic, 'kein json');
      client.deliver(topic, JSON.stringify({ id: 1 }));
      client.deliver(topic, JSON.stringify({ id: 2 }));

      expect(console.error).toHaveBeenCalledTimes(2);
      expect(handler).toHaveBeenCalledWith({ id: 2 });
    }));
  });
});
