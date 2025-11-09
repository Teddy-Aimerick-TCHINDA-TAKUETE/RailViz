import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainsService } from '../service/trains.service';
import { TrainDTO, Sig } from '../service/models';

@Component({
  selector: 'app-trains-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trains-panel.component.html',
  styles: [`
    .panel{position:fixed; top:16px; right:16px; width:280px;
           background:#fff; border-radius:12px; padding:12px;
           box-shadow:0 6px 18px rgba(0,0,0,.15); 
           font-family:system-ui; z-index:1200;}
    .row{display:flex; gap:10px; width:100%; text-align:left; border:0; background:transparent;
         padding:8px; border-radius:10px; cursor:pointer; align-items:flex-start}
    .row:hover{background:#f4f6ff}
    .dot{width:10px;height:10px;border-radius:50%; margin-top:6px; box-shadow:0 0 0 2px #fff, 0 0 0 3px rgba(0,0,0,.06)}
    .id{font-weight:600}
    .meta{font-size:12px;color:#555}
    .badge{display:inline-block; font-size:11px; padding:2px 6px; border-radius:999px; background:#eef2ff; color:#3730a3; margin-left:6px}
    .hd{display:flex; align-items:center; gap:6px}
    .new{padding:6px 10px;border-radius:10px;border:1px solid #ddd;background:#f8f9ff;cursor:pointer}
    button{padding:6px 10px;border-radius:10px;border:1px solid #ddd;background:#f8f9ff;cursor:pointer}
  `]
})
export class TrainsPanelComponent {
  trains: TrainDTO[] = [];

  @Output() center = new EventEmitter<TrainDTO>();
  @Output() newTrain = new EventEmitter<void>();
  @Output() changeSpeed = new EventEmitter<TrainDTO>();
  @Output() deleteTrain = new EventEmitter<TrainDTO>();

  constructor(trainsSvc: TrainsService) {
    trainsSvc.trains$.subscribe(list => this.trains = list);
  }

  signalColor(sig: Sig): string {
    switch (sig) {
      case 'GREEN':  return '#10b981';
      case 'YELLOW': return '#f59e0b';
      case 'RED':    return '#ef4444';
    }
  }

  r(v: number): number { return Math.round(v); }

}
