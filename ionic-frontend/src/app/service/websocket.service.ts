import { effect, Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { Group } from '../model/group';
import { GroupService } from './group.service';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private socketClient!: Client;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private connectionState = new BehaviorSubject<boolean>(false);
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private activeGroup: Group = this.groupService.activeGroup();

  constructor(private groupService: GroupService) {
    effect(() => {
      this.activeGroup = this.groupService.activeGroup();
    })
  }

  connect(token: string) {
    if (this.socketClient?.connected) {
      console.log('WebSocket bereits verbunden.');
      return;
    }

    this.socketClient = new Client({
      webSocketFactory: () => new SockJS(`${environment.websocketUrl}`, {
        transports: ["websocket"],
      }),
      connectHeaders: { Authorization: token },
      debug: (str) => str.includes("Authorization") ? console.log(">>> AUTHENTICATION") : console.log(str),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('WebSocket verbunden!');
        this.connectionState.next(true);
        this.reconnectAttempts = 0;
      },
      onStompError: (frame) => console.error('STOMP-Fehler:', frame),
      onWebSocketClose: () => {
        console.warn('WebSocket geschlossen');
        this.connectionState.next(false);
        // this.handleReconnect(token);
      },
    });

    this.socketClient.activate();
  }

  private handleReconnect(token: string) {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Reconnect-Versuch #${this.reconnectAttempts} in 5 Sekunden...`);
      setTimeout(() => {
        this.connect(token);
      }, 5000);
    } else {
      console.warn('Max. Anzahl an Reconnect-Versuchen erreicht.');
    }
  }

  subscribe(topic: string, callback: (message: any) => void) {    
    const subscription = this.socketClient.subscribe(topic, (message) => {
      const parsedData = JSON.parse(message.body);

      const pattern = /list|item|items/;
      const groupId = parsedData instanceof Array ? parsedData[0].groupId : parsedData.groupId;

      if (groupId !== this.activeGroup.id && pattern.test(topic)) {
        return;
      }

      callback(message);
    });

    this.subscriptions.set(topic, subscription);
  }

  unsubscribeAll() {
    this.subscriptions.forEach((sub) => 
      sub.unsubscribe()
    );
    this.subscriptions.clear();
  }

  disconnect() {
    this.unsubscribeAll();
    this.socketClient.deactivate();
    this.connectionState.next(false);
  }

  getConnectionState(): Observable<boolean> {
    return this.connectionState.asObservable();
  }
}
