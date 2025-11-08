import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouteDTO, RouteWsEvent } from './models';
import { WsService } from './ws.service';


const API = 'http://localhost:8080'; //

@Injectable({ providedIn: 'root' })
export class RoutesService {
  private liveConnected = false;

  constructor(
    private http: HttpClient,
    private ws: WsService
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

  connectLive(onTick?: (t: RouteDTO) => void) {
    if (this.liveConnected) return;
    this.liveConnected = true;

    this.ws.connectRoute((ev: RouteWsEvent) => {
      if (ev.type === 'ADD') {
        onTick?.(ev.route);
      }
    });
  }

}
