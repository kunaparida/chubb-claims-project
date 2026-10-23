import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authResponse } from '../testing';
import { AuthService } from './auth.service';
import { authGuard, homeRedirectGuard, roleGuard } from './guards';
import { Role } from './models';

describe('route guards', () => {
  let auth: AuthService;
  let router: Router;

  const run = (guard: CanActivateFn) =>
    TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  const url = (result: unknown) => router.serializeUrl(result as UrlTree);

  const signInAs = (role: Role) => {
    auth.login('a@b.c', 'pw').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush(authResponse(role));
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('authGuard sends anonymous visitors to /login and lets signed-in users through', () => {
    expect(url(run(authGuard))).toBe('/login');
    signInAs('CLAIMANT');
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard admits a matching role', () => {
    signInAs('MANAGER');
    expect(run(roleGuard('MANAGER'))).toBe(true);
    expect(run(roleGuard('OFFICER', 'MANAGER'))).toBe(true);
  });

  it('roleGuard sends a wrong-role user to their own home rather than an error page', () => {
    signInAs('CLAIMANT');
    expect(url(run(roleGuard('OFFICER', 'MANAGER')))).toBe('/claims');
  });

  it('roleGuard sends anonymous visitors to /login', () => {
    expect(url(run(roleGuard('MANAGER')))).toBe('/login');
  });

  it('homeRedirectGuard sends "/" to the role home, or to /login when signed out', () => {
    expect(url(run(homeRedirectGuard))).toBe('/login');
    signInAs('OFFICER');
    expect(url(run(homeRedirectGuard))).toBe('/queue');
  });
});
