import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TrainsService } from '../service/trains.service';
import { TrainDTO } from '../service/models';

@Component({
  selector: 'app-trains-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trains-panel.component.html',
  styles: [`
    .panel{position:fixed; top:16px; right:16px; width:260px;
           background:#fff; border-radius:12px; padding:12px;
           box-shadow:0 6px 18px rgba(0,0,0,.15);
           font-family:system-ui; z-index:1200;}
    .row{display:block;width:100%;text-align:left;border:0;background:transparent;padding:8px;border-radius:8px;cursor:pointer}
    .row:hover{background:#f4f6ff}
    .id{font-weight:600}
    .meta{font-size:12px;color:#555}
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

  constructor(private api: TrainsService) {
    this.api.list().subscribe(t => this.trains = t);
  }
}
