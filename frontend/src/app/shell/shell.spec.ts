import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from '../core/auth.interceptor';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';
import { authResponse, storeSession } from '../testing';
import { Shell } from './shell';

describe('Shell', () => {
  let http: HttpTestingController;

  const open = async (role: Role) => {
    storeSession(role);
    TestBed.configureTestingModule({
      imports: [Shell],
      // the same interceptor the real app registers, so a 401 from /api/auth/me really does sign the user out
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(Shell);
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    return fixture;
  };

  afterEach(() => localStorage.clear());

  it.each([
    ['CLAIMANT', ['My claims', 'Report an incident'], 'Claimant'],
    ['OFFICER', ['Work queue', 'Assigned to me'], 'Claims officer'],
    ['MANAGER', ['Dashboard', 'Work queue', 'All claims'], 'Manager'],
  ] as [Role, string[], string][])('%s gets the %j navigation', async (role, links, label) => {
    const fixture = await open(role);
    http.expectOne('/api/auth/me').flush({ id: 1, email: 'x@y.z', fullName: 'Someone', role });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim())).toEqual(links);
    expect(el.querySelector('.user')?.textContent).toContain(label);
  });

  it('re-validates the stored session with GET /api/auth/me and follows a role change', async () => {
    const fixture = await open('CLAIMANT');
    http.expectOne('/api/auth/me').flush({ id: 1, email: 'x@y.z', fullName: 'Promoted Person', role: 'MANAGER' });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(TestBed.inject(AuthService).user()?.role).toBe('MANAGER');
    expect(Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim())).toContain('Dashboard');
  });

  it('a 401 from the profile lookup (expired or deleted account) signs the user out', async () => {
    const fixture = await open('OFFICER');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http.expectOne('/api/auth/me').flush({ detail: 'Authentication required' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('Sign out clears the session', async () => {
    const fixture = await open('OFFICER');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http.expectOne('/api/auth/me').flush(authResponse('OFFICER'));
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.link')?.click();
    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });
});
