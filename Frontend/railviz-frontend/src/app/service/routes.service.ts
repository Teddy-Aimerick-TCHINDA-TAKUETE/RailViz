import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { RouteDTO, RouteWsEvent } from './models';


const API = 'http://localhost:8080';
const WS = `${API}/ws`;

@Injectable({ providedIn: 'root' })
export class RoutesService {
  private liveConnected = false;
  private client!: Client;
  private _routes$ = new BehaviorSubject<RouteDTO[]>([]);
  routes$ = this._routes$.asObservable();

  constructor(
    private http: HttpClient
  ) {}

  list() { return this.http.get<RouteDTO[]>(`${API}/api/routes`); }

  add(route: RouteDTO) {
    return this.http.post(`${API}/api/routes`, route);
  }

  update(route: RouteDTO){ 
    return this.http.put(`${API}/api/routes/${route.id}`, route); 
  }
  delete(id: string){ 
    return this.http.delete(`${API}/api/routes/${id}`); 
  }

  connectLive(){
    if (this.client) return;
    this.client = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 3000
    });
    this.client.onConnect = () => {
      this.client!.subscribe('/topic/routes', msg => {
        const ev = JSON.parse(msg.body) as RouteWsEvent;
        const curr = this._routes$.value.slice();
        if (ev.type === 'ADD'){
          // éviter doublons si GET initial déjà présent
          if (!curr.some(r => r.id === ev.route.id)) curr.push(ev.route);
        } else if (ev.type === 'UPDATE'){
          const i = curr.findIndex(r => r.id === ev.route.id);
          if (i>=0) curr[i] = ev.route;
        } else if (ev.type === 'DELETE'){
          const i = curr.findIndex(r => r.id === ev.route.id);
          if (i>=0) curr.splice(i,1);
        }
        this._routes$.next(curr);
      });
    };
    this.client.activate();
  }

  // à appeler une fois au bootstrap (MapComponent)
  primeInitialList(){
    this.list().subscribe(rs => this._routes$.next(rs));
  }

}
