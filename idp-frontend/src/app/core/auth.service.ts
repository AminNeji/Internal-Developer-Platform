import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';

export interface CurrentUser {
  login: string;
  avatarUrl: string;
}

// The backend keeps the login in a session cookie, so there is no token to store here:
// the browser sends the cookie on every /api call by itself.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  // false until /api/me has answered, so the sign-in screen doesn't flash for logged-in users
  readonly checked = signal(false);

  // GET /api/me - 200 means a session exists, 401 means sign in
  load(): void {
    this.http.get<CurrentUser>('/api/me').subscribe({
      next: user => {
        this.user.set(user);
        this.checked.set(true);
      },
      error: () => {
        this.user.set(null);
        this.checked.set(true);
      },
    });
  }

  // a full page navigation, not an http call: the browser has to follow the redirects to GitHub and back
  login(): void {
    window.location.href = '/api/oauth2/authorization/github';
  }

  logout(): void {
    this.http.post('/api/logout', null).subscribe(() => this.user.set(null));
  }

  // called by the interceptor when a request comes back 401 (the session expired)
  signedOut(): void {
    this.user.set(null);
  }
}
