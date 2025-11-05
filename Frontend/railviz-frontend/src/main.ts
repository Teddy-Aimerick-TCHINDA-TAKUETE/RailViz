import { enableProdMode } from '@angular/core';
import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';

import { AppModule } from './app/app.module';
import { environment } from './environments/environment';

import * as L from 'leaflet';

if (environment.production) {
  enableProdMode();
}

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));

(L.Icon.Default.prototype as any)._getIconUrl = function(name: string) {
  const map: Record<string, string> = {
    'icon-retina': 'assets/leaflet/marker-icon-2x.png',
    'icon':        'assets/leaflet/marker-icon.png',
    'shadow':      'assets/leaflet/marker-shadow.png'
  };
  return map[name];
};
