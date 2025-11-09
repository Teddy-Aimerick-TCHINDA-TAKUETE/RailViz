import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { Client } from '@stomp/stompjs';
import { TrainDTO, CreateTrainCommand, UpdateTrainCommand, TrainWsEvent } from './models';

const API = 'http://localhost:8080';
const WS = `${API}/ws`;

@Injectable({ providedIn: 'root' })
export class TrainsService {
  private clientAdmin?: Client; // flux admin (CRUD)
  private clientTelem?: Client; // flux télémétrie
  private map = new Map<string, TrainDTO>();
  private _trains$ = new BehaviorSubject<TrainDTO[]>([]);
  trains$ = this._trains$.asObservable();

  constructor(private http: HttpClient) {
    this.refresh();
    this.connectAdmin();
  }

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

  update(id: string, cmd: UpdateTrainCommand) {
    return this.http.patch(`${API}/api/trains/${id}`, cmd);
  }

  delete(id: string){ 
    return this.http.delete(`${API}/api/trains/${id}`); 
  }

  private refresh() {
    this.list().subscribe(ts => this._trains$.next(ts));
  }

  connectAdmin() {
    if (this.clientAdmin) return;
    this.clientAdmin = new Client({
      webSocketFactory: () => new WebSocket('ws://localhost:8080/ws'),
      reconnectDelay: 3000
    });
    this.clientAdmin.onConnect = () => {
      this.clientAdmin!.subscribe('/topic/trains', msg => {
        const ev = JSON.parse(msg.body) as TrainWsEvent;
        this.applyTrainEvent(ev);
      });
    };
    this.clientAdmin.activate();
  }

  /** Met à jour le cache local sans recharger toute la liste */
  private applyTrainEvent(ev: TrainWsEvent) {
    const cur = this._trains$.getValue();
    if (ev.type === 'ADD') {
      this.upsert(ev.train);
      if (!cur.find(t => t.id === ev.train.id)) this._trains$.next([...cur, ev.train]);
    } else if (ev.type === 'UPDATE') {
      this.upsert(ev.train);
      this._trains$.next(cur.map(t => t.id === ev.train.id ? ev.train : t));
    } else if (ev.type === 'DELETE') {
      this.remove(ev.train.id);
      this._trains$.next(cur.filter(t => t.id !== ev.train.id));
    }
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
        this.upsert(t);
        onTelem(t);
      });
    };
    this.clientTelem.activate();
  }

  /** --------- Store helpers --------- */
  private emit() {
    this._trains$.next(Array.from(this.map.values()));
  }
  private upsert(t: TrainDTO) {
    const prev = this.map.get(t.id);
    // merge simple pour conserver champs non envoyés selon les flux
    this.map.set(t.id, t);
    this.emit();
  }

  private upsertLocal(t: TrainDTO) {
    const cur = this._trains$.getValue();
    const i = cur.findIndex(x => x.id === t.id);
    if (i === -1) this._trains$.next([...cur, t]);
    else {
      const next = cur.slice(); next[i] = t;
      this._trains$.next(next);
    }
  }

  private remove(id: string) {
    this.map.delete(id);
    this.emit();
  }

  /** Snapshot utile pour les vérifs (ex: doublon d'ID côté UI) */
  getSnapshot(): TrainDTO[] { return Array.from(this.map.values()); }
  exists(id: string): boolean { return this.map.has(id); }

  /** --------- Bootstrap initial --------- */
  primeInitialList() {
    this.list().subscribe(ts => {
      this.map.clear();
      ts.forEach(t => this.map.set(t.id, t));
      this.emit();
    });
  }


  // primeInitialList(){
  //   this.list().subscribe(ts => this._trains$.next(ts));
  // }
}
