import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { TrainDTO, CreateTrainCommand, TrainWsEvent } from './models';

const API = 'http://localhost:8080';
const WS = `${API}/ws`;

@Injectable({ providedIn: 'root' })
export class TrainsService {
  private liveConnected = false;
  private client!: Client;
  private clientAdmin?: Client; // flux admin (CRUD)
  private clientTelem?: Client; // flux télémétrie
  private _trains$ = new BehaviorSubject<TrainDTO[]>([]);
  trains$ = this._trains$.asObservable();

  constructor(
    private http: HttpClient,
  ) {}

  /** REST */
  list() {
    return this.http.get<TrainDTO[]>(`${API}/api/trains`);
  }
  create(cmd: CreateTrainCommand) {
    return this.http.post(`${API}/api/trains`, cmd);
  }
  setSpeed(id: string, lineSpeedKmh: number) {
    return this.http.patch(`${API}/api/trains/${id}/speed`, { lineSpeedKmh });
  }

  delete(id: string){ 
    return this.http.delete(`${API}/api/trains/${id}`); 
  }

  // WebSocket admin (ADD/UPDATE/DELETE)
  connectAdmin(){
    if (this.clientAdmin) return;
    this.clientAdmin = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 3000
    });
    this.clientAdmin.onConnect = () => {
      this.clientAdmin!.subscribe('/topic/trains', msg => {
        const ev = JSON.parse(msg.body) as TrainWsEvent;
        const curr = this._trains$.value.slice();
        if (ev.type === 'ADD'){
          const i = curr.findIndex(t => t.id===ev.train.id);
          if (i<0) curr.push(ev.train); else curr[i] = ev.train;
        } else if (ev.type === 'UPDATE'){
          const i = curr.findIndex(t => t.id===ev.train.id);
          if (i>=0) curr[i] = ev.train;
        } else if (ev.type === 'DELETE'){
          const i = curr.findIndex(t => t.id===ev.train.id);
          if (i>=0) curr.splice(i,1);
        }
        this._trains$.next(curr);
      });
    };
    this.clientAdmin.activate();
  }

  // Télémétrie en continu (si tu veux aussi animer les marqueurs)
  connectTelemetry(onTelem: (t: TrainDTO)=>void){
    if (this.clientTelem) return;
    this.clientTelem = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 3000
    });
    this.clientTelem.onConnect = () => {
      this.clientTelem!.subscribe('/topic/telemetry', msg => {
        const t = JSON.parse(msg.body) as TrainDTO;
        onTelem(t);
      });
    };
    this.clientTelem.activate();
  }

  primeInitialList(){
    this.list().subscribe(ts => this._trains$.next(ts));
  }
}
