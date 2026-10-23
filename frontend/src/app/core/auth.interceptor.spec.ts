import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authResponse } from '../testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  const signIn = () => {
    auth.login('a@b.c', 'pw').subscribe();
    http.expectOne('/api/auth/login').flush(authResponse('OFFICER'));
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('adds the bearer token to API calls once signed in', () => {
    signIn();
    client.get('/api/claims').subscribe();
    expect(http.expectOne('/api/claims').request.headers.get('Authorization')).toBe('Bearer token-for-OFFICER');
  });

  it('adds nothing when signed out', () => {
    client.get('/api/claims').subscribe({ error: () => undefined });
    expect(http.expectOne('/api/claims').request.headers.has('Authorization')).toBe(false);
  });

  it('never sends the token to login/register or to another origin', () => {
    signIn();
    client.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    expect(http.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);

    client.post('/api/auth/register', {}).subscribe({ error: () => undefined });
    expect(http.expectOne('/api/auth/register').request.headers.has('Authorization')).toBe(false);

    client.get('https://elsewhere.example.com/api/claims').subscribe();
    expect(http.expectOne('https://elsewhere.example.com/api/claims').request.headers.has('Authorization')).toBe(false);
  });

  it('a 401 on an API call ends the session and goes to the login page', () => {
    signIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    client.get('/api/claims').subscribe({ error: () => undefined });
    http.expectOne('/api/claims').flush({ detail: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('a 401 from login is just wrong credentials, not an expired session', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    client.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([403, 404, 409])('a %i passes through and keeps the session', (status) => {
    signIn();
    let seen = 0;
    client.get('/api/claims/1').subscribe({ error: (e) => (seen = e.status) });
    http.expectOne('/api/claims/1').flush({ detail: 'nope' }, { status, statusText: 'x' });

    expect(seen).toBe(status);
    expect(auth.isLoggedIn()).toBe(true);
  });
});
