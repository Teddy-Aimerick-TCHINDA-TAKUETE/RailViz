export type RouteDTO = { id: string; points: [number, number][] };

export type Sig = 'GREEN'|'YELLOW'|'RED';

export type TrainDTO = {
  id: string;
  lat: number;
  lon: number;
  speedKmh: number;
  signal: Sig;
  routeId: String;
};

export type CreateTrainCommand = {
  trainId: string;
  routeId: string;
  lineSpeedKmh: number;
  startSeg?: number;
  startProgress?: number;
};

export type UpdateTrainCommand = {
  routeId: string;
  lineSpeedKmh: number;
  startSeg?: number;
  startProgress?: number;
  accel?: number;
  decel?: number;
};

export type RouteWsEvent = {
  type: 'ADD' | 'UPDATE' | 'DELETE';
  route: RouteDTO;
};

export type TrainWsEvent = {
  type: 'ADD' | 'UPDATE' | 'DELETE';
  train: TrainDTO;
};