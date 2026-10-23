import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authResponse, type } from '../../testing';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const submitButton = () => el.querySelector<HTMLButtonElement>('button.primary') as HTMLButtonElement;
  const submit = async () => {
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(Login);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
  });

  afterEach(() => localStorage.clear());

  it('cannot be submitted until the email is valid and a password is given', async () => {
    expect(submitButton().disabled).toBe(true);
    type(el, '#email', 'not-an-email');
    type(el, '#password', 'pw');
    await fixture.whenStable();
    expect(submitButton().disabled).toBe(true);

    type(el, '#email', 'officer@claims.local');
    await fixture.whenStable();
    expect(submitButton().disabled).toBe(false);
  });

  it('a demo button fills the seeded officer account', async () => {
    const buttons = Array.from(el.querySelectorAll<HTMLButtonElement>('.demo-list button'));
    expect(buttons.map((b) => b.textContent?.trim())).toEqual([
      'Officer — officer@claims.local',
      'Manager — manager@claims.local',
    ]);
    buttons[0].click();
    await fixture.whenStable();

    expect(el.querySelector<HTMLInputElement>('#email')?.value).toBe('officer@claims.local');
    expect(el.querySelector<HTMLInputElement>('#password')?.value).toBe('ChangeMe123!');
  });

  it('signs in and lands on the role home page (officer -> /queue)', async () => {
    type(el, '#email', 'officer@claims.local');
    type(el, '#password', 'ChangeMe123!');
    await fixture.whenStable();
    await submit();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'officer@claims.local', password: 'ChangeMe123!' });
    req.flush(authResponse('OFFICER'));
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/queue');
  });

  it('shows the backend message for wrong credentials and lets the user retry', async () => {
    type(el, '#email', 'officer@claims.local');
    type(el, '#password', 'wrong');
    await fixture.whenStable();
    await submit();

    http.expectOne('/api/auth/login').flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Invalid email or password');
    expect(submitButton().disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('says the backend is unreachable when it is down', async () => {
    type(el, '#email', 'officer@claims.local');
    type(el, '#password', 'pw');
    await fixture.whenStable();
    await submit();

    http.expectOne('/api/auth/login').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Cannot reach the server');
  });
});
