import { Component, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { switchMap } from 'rxjs/operators';
import * as L from 'leaflet';

import { TrainsPanelComponent } from './trains-panel.component';
import { RoutesPanelComponent } from './routes-panel.component';

import { RoutesService } from '../service/routes.service';
import { TrainsService } from '../service/trains.service';
import { RouteDTO, TrainDTO } from '../service/models';

type Sig = 'GREEN'|'YELLOW'|'RED';

@Component({
  selector: 'app-map',
  standalone: true,
  imports: [CommonModule, FormsModule, TrainsPanelComponent, RoutesPanelComponent],
  templateUrl: './map.component.html'
})
export class MapComponent implements AfterViewInit {
  private map!: L.Map;
  private trainTrails = new Map<string, L.Polyline>();
  private routePolylines = new Map<string, L.Polyline>();
  private markers = new Map<string, L.Marker>();
  private states  = new Map<string, Sig>();

  routes: RouteDTO[] = [];
  showAlertsOnly = false;

  // création de route (draw)
  recording = false;
  recorded: [number,number][] = [];
  newRouteId = 'R-001';
  private tempLine?: L.Polyline;

  // panneau "nouveau train"
  trains: TrainDTO[] = [];
  newTrainOpen = false;
  nt_trainId = 'TGV-123';
  nt_routeId = '';
  nt_lineSpeedKmh = 120;
  nt_startSeg?: number;
  nt_startProgress?: number;

  constructor(
    private routesService: RoutesService,
    private trainsService: TrainsService
  ) {}

  ngAfterViewInit(): void {
    this.map = L.map('map').setView([48.8566, 2.3522], 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    L.tileLayer('https://tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenRailwayMap contributors'
    }).addTo(this.map);

    // routes + trains au chargement
    this.routesService.list().subscribe(routes => this.drawRoutes(routes));
    this.loadRoutes();
    this.loadTrains();

    // ✅ WebSocket: on reçoit directement TrainDTO
    this.trainsService.connectLive((t) => {
      this.upsertTrain(t);
      if (this.showAlertsOnly) this.applyFilterFor(t.id);
    });

    this.routesService.connectLive((r) => {
      // a) mettre à jour la collection → le panneau voit la nouvelle route
      this.routes = [...this.routes, r];
      // b) tracer la polyligne immédiatement
      const line = L.polyline(r.points.map(p => [p[0], p[1]]), {
        color: '#0ea5e9', weight: 3, opacity: .8
      }).addTo(this.map);
      line.bringToBack();
      this.routePolylines.set(r.id, line);
    });
  }

  private drawRoutes(routes: RouteDTO[]) {
    this.routes = routes;
    const palette = ['#0ea5e9', '#22c55e', '#a855f7', '#f59e0b', '#ef4444'];
    routes.forEach((r, i) => {
      const line = L.polyline(r.points.map(p => [p[0], p[1]]), {
        color: palette[i % palette.length], weight: 3, opacity: 0.8
      }).addTo(this.map);
      line.bringToBack();
      this.routePolylines.set(r.id, line);
    });
  }

  loadRoutes() {
    this.routesService.list().subscribe({
      next: (rs) => this.routes = rs,
      error: (e) => console.error(e)
    });
  }

  loadTrains() {
    this.trainsService.list().subscribe({
      next: (ts) => this.trains = ts,
      error: (e) => console.error(e)
    });
  }

  // ---- couleur pastille (DivIcon) + tooltip coloré
  private iconFor(state: Sig) {
    const color =
      state === 'GREEN'  ? '#10b981' :
      state === 'YELLOW' ? '#f59e0b' : '#ef4444';
    const html = `<div style="
      width:18px;height:18px;border-radius:50%;
      background:${color}; border:2px solid #fff; box-shadow:0 0 0 2px rgba(0,0,0,.25);
    "></div>`;
    return L.divIcon({ html, className: '', iconSize: [18,18], iconAnchor: [9,9] });
  }

  private tooltipClass(state: Sig) {
    return state === 'GREEN'  ? 'tt-green'
         : state === 'YELLOW' ? 'tt-yellow'
         : 'tt-red';
  }

  private colorForTrain(id: string): string {
    const colors = ['#0ea5e9','#22c55e','#a855f7','#f59e0b','#ef4444','#14b8a6','#e11d48'];
    let h = 0; for (let i=0;i<id.length;i++) h = (h*31 + id.charCodeAt(i)) >>> 0;
    return colors[h % colors.length];
  }

  // ✅ TrainDTO au lieu de TrainEvent
  private upsertTrain(ev: TrainDTO) {
    const state = ev.signal as Sig;
    this.states.set(ev.id, state);

    const ttClass = `leaflet-tooltip ${this.tooltipClass(state)}`;
    const htmlInfo = `${ev.id}<br>${Math.round(ev.speedKmh)} km/h · ${state}`;
    let mk = this.markers.get(ev.id);

    if (!mk) {
      mk = L.marker([ev.lat, ev.lon], { icon: this.iconFor(state) })
        .addTo(this.map)
        .bindTooltip(htmlInfo, { className: ttClass, direction:'top' });
      this.markers.set(ev.id, mk);

      // trail
      const trail = L.polyline([], { color: this.colorForTrain(ev.id), weight: 4, opacity: .6 }).addTo(this.map);
      trail.bringToBack();
      this.trainTrails.set(ev.id, trail);
    } else {
      mk.setLatLng([ev.lat, ev.lon]);
      mk.setIcon(this.iconFor(state));
      const tt = (mk as any).getTooltip?.();
      if (tt) { tt.setContent(htmlInfo); tt.options.className = ttClass; tt.update(); }
    }

    // trail append
    const trail = this.trainTrails.get(ev.id)!;
    const pts = (trail.getLatLngs() as L.LatLngLiteral[]).slice();
    pts.push({lat: ev.lat, lng: ev.lon}); if (pts.length>250) pts.shift();
    trail.setLatLngs(pts);

    if (this.showAlertsOnly) this.applyFilterFor(ev.id);
  }

  // ---- Filtre alertes uniquement
  applyFilter() {
    for (const [id] of this.markers) this.applyFilterFor(id);
  }

  private applyFilterFor(id: string) {
    const mk = this.markers.get(id);
    if (!mk) return;
    const state = this.states.get(id) as Sig | undefined;

    const shouldShow = !this.showAlertsOnly || (state === 'YELLOW' || state === 'RED');
    const onMap = this.map.hasLayer(mk);

    if (shouldShow && !onMap) mk.addTo(this.map);
    if (!shouldShow && onMap) this.map.removeLayer(mk);

    const trail = this.trainTrails.get(id);
    if (trail) {
      const on = this.map.hasLayer(trail);
      if (shouldShow && !on) trail.addTo(this.map);
      if (!shouldShow && on) this.map.removeLayer(trail);
    }
  }

  // appelé par le panneau (clic sur un train)
  centerOn(t: {lat:number;lon:number}) {
    this.map.setView([t.lat, t.lon], Math.max(this.map.getZoom(), 13), { animate: true });
  }

  // appelé par le panneau (clic sur une route)
  centerRoute(r: RouteDTO) {
    // 1) si on a déjà dessiné la polyligne → fitBounds direct
    const existing = this.routePolylines.get(r.id);
    if (existing) {
      this.map.fitBounds(existing.getBounds(), { animate: true, padding: [40, 40] });
      return;
    }

    // 2) sinon, on la trace à la volée puis on centre
    const line = L.polyline(r.points.map(p => [p[0], p[1]]), {
      color: '#0ea5e9', weight: 3, opacity: 0.8
    }).addTo(this.map);
    line.bringToBack();
    this.routePolylines.set(r.id, line);
    this.map.fitBounds(line.getBounds(), { animate: true, padding: [40, 40] });
  }

  // ===== Création de route =====
  toggleRecord() {
    this.recording = !this.recording;
    this.recorded = [];
    if (this.recording) {
      this.map.on('click', this.onClickRecord);
    } else {
      this.map.off('click', this.onClickRecord);
    }
  }

  openNewRoute() {
    this.recording = true;
    this.recorded = [];
    this.tempLine = L.polyline([], { color: '#0ea5e9', weight: 3, opacity: 0.9 }).addTo(this.map);
    this.map.on('click', this.onClickRecord);
  }

  cancelNewRoute() {
    this.recording = false;
    this.map.off('click', this.onClickRecord);
    if (this.tempLine) { this.map.removeLayer(this.tempLine); this.tempLine = undefined; }
    this.recorded = [];
  }

  onClickRecord = (e: L.LeafletMouseEvent) => {
    this.recorded.push([e.latlng.lat, e.latlng.lng]);
    this.tempLine?.setLatLngs(this.recorded.map(p => ({lat:p[0], lng:p[1]})));
  };

  saveNewRoute() {
    if (this.recorded.length < 2) return;
    const id = this.newRouteId.trim();
    if (!id) return;
    const checkId = this.routes.some(r => r.id === id);
    if (checkId) { alert('Cette route existe deja.'); return; }

    this.routesService.add({ id: id, points: this.recorded })
      .pipe(switchMap(() => this.routesService.list()))
      .subscribe({
        next: rs => { 
          this.routes = rs;
          const line = L.polyline(this.recorded, { color: '#0ea5e9', weight: 3, opacity: .8 }).addTo(this.map);
          line.bringToBack();
          this.routePolylines.set(id, line);
          this.cancelNewRoute();
        },
        error: err => alert('Erreur création route: ' + (err?.error || err?.message))
      });
  }

  // ===== Panneau "Nouveau train" (panel droit) =====
  openNewTrainPanel() {
    this.newTrainOpen = true;
    if (!this.nt_routeId && this.routes.length) this.nt_routeId = this.routes[0].id;
  }
  cancelNewTrainPanel() { this.newTrainOpen = false; }

  submitNewTrain() {
    const id = this.nt_trainId.trim();
    if (!id) return;

    const routeOk = this.routes.some(r => r.id === this.nt_routeId);
    if (!routeOk) { alert('Sélectionne une route existante.'); return; }

    if (this.trains.some(t => t.id === id)) {
      alert('trainId déjà utilisé.'); return;
    }

    this.trainsService.create({
      trainId: id, routeId: this.nt_routeId, lineSpeedKmh: this.nt_lineSpeedKmh,
      startSeg: this.nt_startSeg, startProgress: this.nt_startProgress
    }).subscribe({
      next: () => { this.newTrainOpen = false; this.loadTrains(); },
      error: err => alert('Erreur création: ' + (err?.error || err?.message))
    });
  }
}
