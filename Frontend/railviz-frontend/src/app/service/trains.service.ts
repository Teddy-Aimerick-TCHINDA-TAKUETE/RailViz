import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TrainDTO, CreateTrainCommand, TrainWsEvent } from './models';
import { TrainStore } from './train-store.service';
import { WsService } from './ws.service';

const API = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class TrainsService {
  private liveConnected = false;

  constructor(
    private http: HttpClient,
    private store: TrainStore,
    private ws: WsService
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

  /** Temps réel: alimente le store + callback optionnel */
  connectLive(onTick?: (t: TrainDTO) => void) {
    if (this.liveConnected) return;
    this.liveConnected = true;

    this.ws.connectTrain((ev: TrainWsEvent) => {
      if (ev.type === 'ADD') {
        this.store.upsert(ev.train);
        onTick?.(ev.train);
      }
    });
  }

  /** Flux des trains (état courant vu par le store) */
  get stream$() {
    return this.store.$;
  }
}
