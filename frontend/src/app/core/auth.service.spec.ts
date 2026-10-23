import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { STORAGE_KEY, authResponse } from '../testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  const stored = (role: 'CLAIMANT' | 'OFFICER' | 'MANAGER', expiresAt: number) => {
    const r = authResponse(role);
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ token: r.token, expiresAt, user: { id: r.userId, email: r.email, fullName: r.fullName, role: r.role } }),
    );
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('starts signed out when nothing is stored', () => {
    const auth = TestBed.inject(AuthService);
    expect(auth.isLoggedIn()).toBe(false);
    expect(auth.user()).toBeNull();
    expect(auth.token()).toBeNull();
  });

  it('login posts the credentials, exposes the user and token, and persists the session', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('priya@x.io', 'pw').subscribe();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'priya@x.io', password: 'pw' });
    req.flush(authResponse('OFFICER'));

    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.token()).toBe('token-for-OFFICER');
    expect(auth.user()).toEqual({ id: 7, email: 'officer@example.com', fullName: 'Priya Nair', role: 'OFFICER' });
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) as string).token).toBe('token-for-OFFICER');
  });

  it('the token expires expiresIn milliseconds after login', () => {
    const before = Date.now();
    const auth = TestBed.inject(AuthService);
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('OFFICER', { expiresIn: 60_000 }));

    const expiresAt = JSON.parse(localStorage.getItem(STORAGE_KEY) as string).expiresAt as number;
    expect(expiresAt).toBeGreaterThanOrEqual(before + 60_000);
    expect(expiresAt).toBeLessThan(Date.now() + 60_000 + 1);
  });

  it('register posts fullName/email/password and signs the new claimant in', () => {
    const auth = TestBed.inject(AuthService);
    auth.register('Chen Wei', 'chen@x.io', 'password1').subscribe();

    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({ fullName: 'Chen Wei', email: 'chen@x.io', password: 'password1' });
    req.flush(authResponse('CLAIMANT'));

    expect(auth.user()?.role).toBe('CLAIMANT');
  });

  it('a failed login leaves the user signed out and stores nothing', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('a@b.c', 'bad').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('logout clears the session and returns to the login page', () => {
    const auth = TestBed.inject(AuthService);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('CLAIMANT'));

    auth.logout();

    expect(auth.isLoggedIn()).toBe(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('restores a valid stored session after a page refresh', () => {
    stored('MANAGER', Date.now() + 60_000);
    const auth = TestBed.inject(AuthService);
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.user()?.role).toBe('MANAGER');
  });

  it('ignores an expired stored session', () => {
    stored('MANAGER', Date.now() - 1);
    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(false);
  });

  it('ignores corrupt stored data instead of crashing', () => {
    localStorage.setItem(STORAGE_KEY, '{not json');
    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(false);
  });

  it('keeps working in memory when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    const auth = TestBed.inject(AuthService);
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('CLAIMANT'));

    expect(auth.isLoggedIn()).toBe(true);
  });

  it('routes each role to its own home page', () => {
    const auth = TestBed.inject(AuthService);
    expect(auth.homeFor('CLAIMANT')).toBe('/claims');
    expect(auth.homeFor('OFFICER')).toBe('/queue');
    expect(auth.homeFor('MANAGER')).toBe('/dashboard');
  });

  describe('refreshProfile (GET /api/auth/me)', () => {
    it('does nothing when signed out', () => {
      TestBed.inject(AuthService).refreshProfile();
      http.expectNone('/api/auth/me');
    });

    it('replaces stale local user data with what the server says (e.g. a changed role)', () => {
      stored('CLAIMANT', Date.now() + 60_000);
      const auth = TestBed.inject(AuthService);

      auth.refreshProfile();
      const req = http.expectOne('/api/auth/me');
      expect(req.request.method).toBe('GET');
      req.flush({ id: 1, email: 'claimant@example.com', fullName: 'Chen Wei', role: 'OFFICER' });

      expect(auth.user()?.role).toBe('OFFICER');
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY) as string).user.role).toBe('OFFICER');
    });

    it('keeps the session when the lookup fails for a non-auth reason', () => {
      stored('CLAIMANT', Date.now() + 60_000);
      const auth = TestBed.inject(AuthService);
      auth.refreshProfile();
      http.expectOne('/api/auth/me').flush({}, { status: 500, statusText: 'Server Error' });
      expect(auth.isLoggedIn()).toBe(true);
    });
  });
});
