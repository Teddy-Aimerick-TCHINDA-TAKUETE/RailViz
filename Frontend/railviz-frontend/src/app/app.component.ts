import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],           // standalone → on importe les dépendances ici
  templateUrl: './app.component.html'
})
export class AppComponent {}
