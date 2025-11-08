import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RoutesService } from '../service/routes.service';
import { RouteDTO } from '../service/models';

@Component({
  selector: 'app-routes-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './routes-panel.component.html',
  styles: [`
    .panel{position:fixed; top:16px; left:16px; width:260px;
           background:#fff; border-radius:12px; padding:12px;
           box-shadow:0 6px 18px rgba(0,0,0,.15);
           font-family:system-ui; z-index:1200;}
    .row{border:1px solid #eee;border-radius:10px;padding:8px;margin-bottom:8px}
    .head{display:flex;justify-content:space-between;align-items:baseline}
    .id{font-weight:600}
    .meta{font-size:12px;color:#555}
    .actions{margin-top:6px}
    button{padding:6px 10px;border-radius:10px;border:1px solid #ddd;background:#f8f9ff;cursor:pointer}
  `]
})
export class RoutesPanelComponent {
  routes: RouteDTO[] = [];

  @Output() newRoute = new EventEmitter<void>();
  @Output() centerRoute = new EventEmitter<RouteDTO>();
  @Output() editRoute = new EventEmitter<RouteDTO>();
  @Output() deleteRoute = new EventEmitter<RouteDTO>();


  constructor(private api: RoutesService) {
    this.api.list().subscribe(r => this.routes = r);
  }

  lengthKm(r: RouteDTO): number {
    let d = 0;
    for (let i=0;i<r.points.length-1;i++){
      d += this.hav(r.points[i], r.points[i+1]);
    }
    return d/1000;
  }
  private hav(a:[number,number], b:[number,number]) {
    const R=6371e3, p1=a[0]*Math.PI/180, p2=b[0]*Math.PI/180, dp=(b[0]-a[0])*Math.PI/180, dl=(b[1]-a[1])*Math.PI/180;
    const s=Math.sin(dp/2)**2+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)**2; return 2*R*Math.atan2(Math.sqrt(s),Math.sqrt(1-s));
  }
}
