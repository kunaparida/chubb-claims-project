import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authResponse, type } from '../../testing';
import { Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const submitButton = () => el.querySelector<HTMLButtonElement>('button.primary') as HTMLButtonElement;
  const fill = async (fullName: string, email: string, password: string) => {
    type(el, '#fullName', fullName);
    type(el, '#email', email);
    type(el, '#password', password);
    await fixture.whenStable();
  };

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(Register);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await fixture.whenStable();
  });

  afterEach(() => localStorage.clear());

  it('needs a name, a valid email and an 8+ character password (the backend minimum)', async () => {
    await fill('Chen Wei', 'chen@x.io', 'short');
    expect(submitButton().disabled).toBe(true);
    await fill('Chen Wei', 'chen@x.io', 'longenough');
    expect(submitButton().disabled).toBe(false);
  });

  it('registers and lands on the claimant home page', async () => {
    await fill('  Chen Wei ', 'chen@x.io', 'longenough');
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({ fullName: 'Chen Wei', email: 'chen@x.io', password: 'longenough' });
    req.flush(authResponse('CLAIMANT'));
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/claims');
  });

  it('shows the backend validation messages beside the fields', async () => {
    await fill('Chen Wei', 'taken@x.io', 'longenough');
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne('/api/auth/register').flush(
      { detail: 'Request validation failed', errors: { email: 'Email is already registered' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.querySelector('.field-error')?.textContent).toContain('Email is already registered');
    expect(navigate).not.toHaveBeenCalled();
  });
});
