import { Component } from '@angular/core';
import * as L from 'leaflet';
import { WsService } from '../service/ws.service';

@Component({
  selector: 'app-map',
  template: '<div id="map" style="height:100vh"></div>'
})
export class MapComponent {

  constructor(private ws: WsService) {}
  ngAfterViewInit() {
  this.ws.connect(ev => {
    // ev: TrainEvent
    this.upsertTrainMarker(ev.trainId, ev.lat, ev.lon);
    // TODO: mettre à jour tes KPI ici
  });
  }

  map!: L.Map;
  trainsLayer = L.layerGroup();

  ngOnInit() {
    this.map = L.map('map').setView([48.8566, 2.3522], 12);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      { attribution: '&copy; OSM' }).addTo(this.map);

    // SURCOUCHE ferroviaire OpenRailwayMap (ex. style standard)
    L.tileLayer('https://tiles.openrailwaymap.org/standard/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenRailwayMap contributors'
    }).addTo(this.map);

    this.trainsLayer.addTo(this.map);
  }

  upsertTrainMarker(id: string, lat: number, lon: number) {
    const key = `train-${id}`;
    const existing = (this.trainsLayer as any)._layers;
    let marker = Object.values(existing).find((m:any)=> m.options && m.options.pane===key) as L.Marker|undefined;

    if (!marker) {
      marker = L.marker([lat, lon], { pane: key });
      marker.bindTooltip(id);
      this.trainsLayer.addLayer(marker);
    } else {
      marker.setLatLng([lat, lon]);
    }
  }
}
