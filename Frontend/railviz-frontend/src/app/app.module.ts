import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppComponent } from './app.component';
import { LeafletModule } from '@bluehalo/ngx-leaflet';


@NgModule({
    declarations: [
        AppComponent
    ],
    imports: [
        BrowserModule,
        LeafletModule
    ],
    providers: [],
    bootstrap: [ AppComponent ]
})
export class AppModule { }
