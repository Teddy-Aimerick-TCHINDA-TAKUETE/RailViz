import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { TrainWsEvent, RouteWsEvent } from './models';

@Injectable({ providedIn: 'root' })
export class WsService {
  client!: Client;

  connectTrain(onEvent: (ev: TrainWsEvent) => void) {
    this.client = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 2000,
    });
    this.client.onConnect = () => {
      this.client!.subscribe('/topic/trains', (msg: IMessage) => {
        const ev: TrainWsEvent = JSON.parse(msg.body);
        onEvent(ev);
      });
    };
    this.client.activate();
  }

  connectRoute(onEvent: (ev: RouteWsEvent) => void) {
    this.client = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 2000,
    });
    this.client.onConnect = () => {
      this.client!.subscribe('/topic/routes', (msg: IMessage) => {
        const ev: RouteWsEvent = JSON.parse(msg.body);
        onEvent(ev);
      });
    };
    this.client.activate();
  }

  disconnect() {
    this.client?.deactivate();
  }
}
