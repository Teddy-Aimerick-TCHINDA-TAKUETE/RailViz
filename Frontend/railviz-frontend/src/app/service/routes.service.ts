import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { RouteDTO, RouteWsEvent } from './models';


const API = 'http://localhost:8080';
const WS = `${API}/ws`;

@Injectable({ providedIn: 'root' })
export class RoutesService {
  private client!: Client;
  private map = new Map<string, RouteDTO>();
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

  /** Store */
  private emit() {
    this._routes$.next(Array.from(this.map.values()));
  }
  private upsert(r: RouteDTO) {
    this.map.set(r.id, r);
    this.emit();
  }
  private remove(id: string) {
    this.map.delete(id);
    this.emit();
  }
  getSnapshot(): RouteDTO[] { return Array.from(this.map.values()); }
  exists(id: string): boolean { return this.map.has(id); }

  /** Init + WS */
  primeInitialList() {
    this.list().subscribe(rs => {
      this.map.clear();
      rs.forEach(r => this.map.set(r.id, r));
      this.emit();
    });
  }

  connectWs() {
    if (this.client) return;
    this.client = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 3000
    });
    this.client.onConnect = () => {
      this.client!.subscribe('/topic/routes', msg => {
        const ev = JSON.parse(msg.body) as RouteWsEvent;
        if (ev.type === 'DELETE') this.remove(ev.route.id);
        else this.upsert(ev.route);
      });
    };
    this.client.activate();
  }

}
