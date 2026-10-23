import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ApiService } from './api.service';
import { AuthResponse, Role, UserProfile } from './models';

interface Session {
  token: string;
  /** Epoch milliseconds when the token stops being valid (login time + the backend's expiresIn). */
  expiresAt: number;
  user: UserProfile;
}

const STORAGE_KEY = 'claims.ui.session';

/** Where each role lands after signing in. */
const HOME: Record<Role, string> = {
  CLAIMANT: '/claims',
  OFFICER: '/queue',
  MANAGER: '/dashboard',
};

/**
 * The signed-in session. The token is kept in memory and in localStorage so a refresh keeps you signed in. Storage can be
 * unavailable (private windows, blocked storage), so every access is guarded: the app then simply forgets you on refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  login(email: string, password: string): Observable<AuthResponse> {
    return this.api.login({ email, password }).pipe(tap((res) => this.start(res)));
  }

  register(fullName: string, email: string, password: string): Observable<AuthResponse> {
    return this.api.register({ fullName, email, password }).pipe(tap((res) => this.start(res)));
  }

  /**
   * Re-reads the profile from the server when the app loads with a stored session, so a changed role or a deleted account is
   * noticed instead of trusting stale local data. A 401 here is handled by the interceptor (it signs the user out).
   */
  refreshProfile(): void {
    if (!this.isLoggedIn()) {
      return;
    }
    this.api.me().subscribe({
      next: (profile) => {
        const current = this.session();
        if (current) {
          const next: Session = { ...current, user: profile };
          this.session.set(next);
          this.persist(next);
        }
      },
      error: () => undefined,
    });
  }

  logout(redirect = true): void {
    this.session.set(null);
    this.persist(null);
    if (redirect) {
      void this.router.navigateByUrl('/login');
    }
  }

  homeFor(role: Role): string {
    return HOME[role];
  }

  private start(res: AuthResponse): void {
    const session: Session = {
      token: res.token,
      expiresAt: Date.now() + res.expiresIn,
      user: { id: res.userId, email: res.email, fullName: res.fullName, role: res.role },
    };
    this.session.set(session);
    this.persist(session);
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const stored = JSON.parse(raw) as Session;
      // an expired token would only produce a 401 on the first request; drop it up front
      return stored?.token && stored.user && stored.expiresAt > Date.now() ? stored : null;
    } catch {
      return null;
    }
  }

  private persist(session: Session | null): void {
    try {
      if (session) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // storage unavailable: the session lives for this page load only
    }
  }
}
