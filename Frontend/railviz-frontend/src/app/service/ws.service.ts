import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client'; // ✅ default import

@Injectable({ providedIn: 'root' })
export class WsService {
  client!: Client;

  connect(cb: (ev: any) => void) {
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws') as unknown as WebSocket, // ✅ cast
      reconnectDelay: 2000,
    });

    this.client.onConnect = () => {
      this.client.subscribe('/topic/telemetry', (m: IMessage) => cb(JSON.parse(m.body)));
    };

    this.client.activate();
  }
}