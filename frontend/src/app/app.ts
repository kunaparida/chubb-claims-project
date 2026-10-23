import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root: only hosts the router. The signed-in layout lives in Shell so the login page can be chrome-free. */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {}
