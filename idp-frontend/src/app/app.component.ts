import { Component, inject, OnInit } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  title = 'idp-frontend';

  readonly auth = inject(AuthService);
  // the backend sends rejected users to /?denied; read it before the router redirects and drops it
  readonly denied = new URLSearchParams(window.location.search).has('denied');

  ngOnInit(): void {
    this.auth.load();
  }
}
