import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { choose, claim, storeSession, type } from '../../testing';
import { NewClaim } from './new-claim';

describe('NewClaim', () => {
  let fixture: ComponentFixture<NewClaim>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const submitButton = () => el.querySelector<HTMLButtonElement>('button.primary') as HTMLButtonElement;
  const iso = (offsetDays: number) => new Date(Date.now() + offsetDays * 86_400_000).toISOString().slice(0, 10);

  const fillValid = async (over: Record<string, string> = {}) => {
    const v = {
      policyNumber: 'POL-2026-00042',
      market: 'sg',
      incidentDate: iso(-2),
      description: 'Rear bumper damage from a parking collision.',
      amountClaimed: '1250.50',
      ...over,
    };
    for (const [id, value] of Object.entries(v)) {
      type(el, '#' + id, value);
    }
    await fixture.whenStable();
  };

  beforeEach(async () => {
    storeSession('CLAIMANT');
    TestBed.configureTestingModule({
      imports: [NewClaim],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(NewClaim);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await fixture.whenStable();
  });

  afterEach(() => localStorage.clear());

  it('takes the claimant name and email from the session — the email cannot be edited', () => {
    expect(el.querySelector<HTMLInputElement>('#claimantName')?.value).toBe('Chen Wei');
    const email = el.querySelector<HTMLInputElement>('#claimantEmail');
    expect(email?.value).toBe('claimant@example.com');
    expect(email?.readOnly).toBe(true);
  });

  it('cannot be submitted empty, and a complete form enables the button', async () => {
    expect(submitButton().disabled).toBe(true);
    await fillValid();
    expect(submitButton().disabled).toBe(false);
  });

  it.each([
    ['a future incident date', { incidentDate: iso(3) }],
    ['a description under 20 characters', { description: 'too short' }],
    ['a one-letter market', { market: 'S' }],
    ['a three-letter market', { market: 'SGP' }],
    ['a zero amount', { amountClaimed: '0' }],
    ['an amount with 3 decimals', { amountClaimed: '10.123' }],
    ['a blank policy number', { policyNumber: '' }],
  ])('rejects %s before calling the backend', async (_label, over) => {
    await fillValid(over);
    expect(submitButton().disabled).toBe(true);
  });

  it('sends exactly what the backend expects: trimmed text, upper-case market, the session email', async () => {
    await fillValid({ policyNumber: '  POL-2026-00042 ' });
    choose(el, '#claimType', 'PROPERTY');
    await fixture.whenStable();
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const req = http.expectOne('/api/claims');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      policyNumber: 'POL-2026-00042',
      claimantName: 'Chen Wei',
      claimantEmail: 'claimant@example.com',
      incidentDate: iso(-2),
      description: 'Rear bumper damage from a parking collision.',
      amountClaimed: 1250.5,
      claimType: 'PROPERTY',
      market: 'SG',
    });
    req.flush(claim({ id: 99 }), { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/claims', 99]);
  });

  it('shows backend field errors beside the inputs and keeps the form editable', async () => {
    await fillValid();
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne('/api/claims').flush(
      { detail: 'Request validation failed', errors: { policyNumber: 'must not be blank', amountClaimed: 'must be greater than zero' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const messages = Array.from(el.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());
    expect(messages).toContain('must not be blank');
    expect(messages).toContain('must be greater than zero');
    expect(el.querySelector('[role=alert]')?.textContent).toContain('correct the highlighted fields');
    expect(submitButton().disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows a non-field backend error (e.g. 403) as a banner', async () => {
    await fillValid();
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectOne('/api/claims').flush(
      { detail: 'Claimant email must match the authenticated account' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Claimant email must match the authenticated account');
  });
});
