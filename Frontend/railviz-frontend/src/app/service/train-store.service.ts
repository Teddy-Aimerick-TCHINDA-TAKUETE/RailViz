import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { TrainDTO } from './models';

@Injectable({ providedIn:'root' })
export class TrainStore {
  private map = new Map<string, TrainDTO>();
  readonly $ = new BehaviorSubject<TrainDTO[]>([]);

  upsert(t: TrainDTO) {
    this.map.set(t.id, t);
    this.$.next(Array.from(this.map.values()));
  }
}
