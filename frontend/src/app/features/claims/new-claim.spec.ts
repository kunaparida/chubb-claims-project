import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { incidentWindow, shiftIsoDate } from '../../core/dates';
import { Flash } from '../../core/flash';
import { choose, claim, claimDocument, storeSession, type } from '../../testing';
import { NewClaim } from './new-claim';

describe('NewClaim', () => {
  let fixture: ComponentFixture<NewClaim>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const submitButton = () => el.querySelector<HTMLButtonElement>('button.primary') as HTMLButtonElement;
  const policy = () => el.querySelector<HTMLInputElement>('#policyNumber')?.value ?? '';
  const range = incidentWindow();
  /** An incident date this many days before the latest date the form accepts. */
  const daysAgo = (n: number) => shiftIsoDate(range.max, -n);

  const fillValid = async (over: Record<string, string> = {}) => {
    const v = {
      market: 'SG',
      incidentDate: daysAgo(2),
      description: 'Rear bumper damage from a parking collision.',
      amountClaimed: '1250.50',
      ...over,
    };
    for (const [id, value] of Object.entries(v)) {
      if (id === 'market') {
        choose(el, '#market', value);
      } else {
        type(el, '#' + id, value);
      }
    }
    await fixture.whenStable();
  };

  const submit = async () => {
    el.querySelector('form')?.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  };
  /** Presses "Submit claim" and says whether a request to create the claim was sent. */
  const attempt = async (): Promise<boolean> => {
    await submit();
    return http.match((r) => r.url === '/api/claims' && r.method === 'POST').length > 0;
  };
  const summary = () =>
    Array.from(el.querySelectorAll('[data-testid=problem-summary] li')).map((li) => li.textContent?.trim());
  const fieldMessages = () => Array.from(el.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());

  const pdf = (name: string, size = 1000) => new File([new Uint8Array(size)], name, { type: 'application/pdf' });
  const pick = async (files: File[]) => {
    const input = el.querySelector<HTMLInputElement>('#documents') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  };
  const chosenNames = () => Array.from(el.querySelectorAll('[data-testid=chosen-files] .file-name')).map((n) => n.textContent?.trim());

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

  describe('policy number', () => {
    it('is generated for the claimant (POL-year-six digits) and cannot be typed over', () => {
      expect(policy()).toMatch(new RegExp(`^POL-${new Date().getFullYear()}-\\d{6}$`));
      expect(el.querySelector<HTMLInputElement>('#policyNumber')?.readOnly).toBe(true);
    });

    it('can be replaced with a fresh number by the button beside it', async () => {
      const first = policy();
      const button = el.querySelector<HTMLButtonElement>('button[aria-label="Generate a new policy number"]') as HTMLButtonElement;
      for (let i = 0; i < 5 && policy() === first; i++) {
        button.click();
        await fixture.whenStable();
      }
      expect(policy()).not.toBe(first);
      expect(policy()).toMatch(/^POL-\d{4}-\d{6}$/);
    });
  });

  describe('market', () => {
    it('is a dropdown of the six markets, none chosen to begin with', () => {
      const select = el.querySelector<HTMLSelectElement>('select#market') as HTMLSelectElement;
      expect(select).not.toBeNull();
      expect(select.value).toBe('');
      const options = Array.from(select.options).filter((o) => o.value !== '');
      expect(options.map((o) => o.value)).toEqual(['SG', 'HK', 'IN', 'MY', 'TH', 'ID']);
      expect(options.map((o) => o.textContent?.trim())).toEqual([
        'Singapore (SG)',
        'Hong Kong (HK)',
        'India (IN)',
        'Malaysia (MY)',
        'Thailand (TH)',
        'Indonesia (ID)',
      ]);
    });
  });

  describe('incident date', () => {
    it('offers only the last seven days, today included', () => {
      const input = el.querySelector<HTMLInputElement>('#incidentDate') as HTMLInputElement;
      expect(input.min).toBe(range.min);
      expect(input.max).toBe(range.max);
      expect(range.min).toBe(daysAgo(7));
    });

    it.each([
      ['today', 0, true],
      ['seven days ago', 7, true],
      ['eight days ago', 8, false],
      ['a month ago', 30, false],
      ['tomorrow', -1, false],
    ])('%s: accepted = %s', async (_label, back, accepted) => {
      await fillValid({ incidentDate: daysAgo(back as number) });
      expect(await attempt()).toBe(accepted);
    });

    it('explains why an older date is refused', async () => {
      await fillValid({ incidentDate: daysAgo(10) });
      await submit();
      expect(fieldMessages()).toContain(`Only incidents from the last 7 days can be reported (from ${range.min}).`);
    });

    it('explains why a future date is refused', async () => {
      await fillValid({ incidentDate: daysAgo(-3) });
      await submit();
      expect(fieldMessages()).toContain('The incident date cannot be in the future.');
    });
  });

  describe('required fields', () => {
    it('are marked with a star, and the market, date, description and amount start empty', () => {
      const stars = Array.from(el.querySelectorAll('label .req')).length;
      expect(stars).toBeGreaterThanOrEqual(7);
      expect(el.querySelector('.req-note')?.textContent).toContain('required');
    });

    it('lets "Submit claim" be pressed on an empty form and says exactly what is missing, instead of silently doing nothing', async () => {
      expect(submitButton().disabled).toBe(false);
      expect(await attempt()).toBe(false);

      expect(summary()).toEqual([
        'Select the market the claim relates to.',
        'Enter the date of the incident.',
        'Describe what happened.',
        'Enter the amount you are claiming.',
      ]);
      expect(el.querySelector('[data-testid=problem-summary]')?.textContent).toContain('Please fix these 4 things before submitting');
      expect(el.querySelector('[data-testid=problem-summary]')?.getAttribute('role')).toBe('alert');
    });

    it('shows each message beside its own field as well, with the field marked invalid for screen readers', async () => {
      await submit();

      expect(fieldMessages()).toEqual([
        'Select the market the claim relates to.',
        'Enter the date of the incident.',
        'Describe what happened.',
        'Enter the amount you are claiming.',
      ]);
      for (const id of ['market', 'incidentDate', 'description', 'amountClaimed']) {
        const field = el.querySelector(`#${id}`);
        expect(field?.getAttribute('aria-invalid'), id).toBe('true');
        expect(field?.getAttribute('aria-describedby'), id).toContain(`${id}-error`);
        expect(el.querySelector(`#${id}-error`), id).not.toBeNull();
      }
    });

    it('moves the keyboard to the first field that needs attention', async () => {
      await submit();
      expect(document.activeElement?.id).toBe('market');
    });

    it('says "this" for a single problem', async () => {
      await fillValid();
      type(el, '#claimantName', '   ');
      await fixture.whenStable();
      await submit();

      expect(summary()).toEqual(['Enter your name.']);
      expect(el.querySelector('[data-testid=problem-summary]')?.textContent).toContain('Please fix this before submitting');
    });

    it.each([
      ['a blank name', { claimantName: '  ' }, 'Enter your name.'],
      ['a description under 20 characters', { description: 'too short' }, 'Describe what happened in at least 20 characters (you have written 9).'],
      ['a description of only spaces', { description: ' '.repeat(30) }, 'Describe what happened.'],
      ['a zero amount', { amountClaimed: '0' }, 'Enter an amount greater than zero with at most two decimals.'],
      ['an amount with 3 decimals', { amountClaimed: '10.123' }, 'Enter an amount greater than zero with at most two decimals.'],
      ['no amount at all', { amountClaimed: '' }, 'Enter the amount you are claiming.'],
      ['no incident date', { incidentDate: '' }, 'Enter the date of the incident.'],
    ])('explains %s', async (_label, over, message) => {
      await fillValid(over);
      expect(await attempt()).toBe(false);
      expect(summary()).toEqual([message]);
    });

    it('shows a field\'s problem as soon as the claimant leaves it, before they try to submit', async () => {
      expect(fieldMessages()).toEqual([]);
      el.querySelector('#market')?.dispatchEvent(new Event('blur'));
      await fixture.whenStable();

      expect(fieldMessages()).toEqual(['Select the market the claim relates to.']);
      expect(el.querySelector('[data-testid=problem-summary]')).toBeNull(); // the summary waits for a submit attempt
    });

    it('updates as the claimant fixes things, and the claim goes through once nothing is left', async () => {
      await submit();
      expect(summary()).toHaveLength(4);

      choose(el, '#market', 'IN');
      type(el, '#incidentDate', daysAgo(1));
      await fixture.whenStable();
      expect(summary()).toEqual(['Describe what happened.', 'Enter the amount you are claiming.']);

      type(el, '#description', 'Windscreen cracked by a stone on the highway.');
      type(el, '#amountClaimed', '300');
      await fixture.whenStable();
      expect(el.querySelector('[data-testid=problem-summary]')).toBeNull();
      expect(fieldMessages()).toEqual([]);

      expect(await attempt()).toBe(true);
    });

    it('is not clickable while a submit is in flight, so a claim cannot be sent twice', async () => {
      await fillValid();
      await submit();
      expect(submitButton().disabled).toBe(true);
      expect(submitButton().textContent?.trim()).toBe('Submitting…');
      http.expectOne('/api/claims');
      await submit();
      http.expectNone('/api/claims');
    });
  });

  describe('supporting documents (optional)', () => {
    it('are not required: a claim goes through with none, and nothing is uploaded', async () => {
      expect(el.querySelector('.optional-tag')?.textContent).toContain('Optional');
      await fillValid();
      await submit();

      http.expectOne('/api/claims').flush(claim({ id: 99 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      http.expectNone('/api/claims/99/documents');
      expect(navigate).toHaveBeenCalledWith(['/claims', 99]);
      expect(TestBed.inject(Flash).take()).toBeNull();
    });

    it('can be chosen, listed with their size, and removed again', async () => {
      await pick([pdf('estimate.pdf', 184_320), pdf('photo.pdf', 2_500_000)]);

      expect(chosenNames()).toEqual(['estimate.pdf', 'photo.pdf']);
      const rows = Array.from(el.querySelectorAll('[data-testid=chosen-files] li')).map((li) => li.textContent ?? '');
      expect(rows[0]).toContain('180 KB');
      expect(rows[1]).toContain('2.4 MB');

      el.querySelector<HTMLButtonElement>('[data-testid=chosen-files] button')?.click();
      await fixture.whenStable();
      expect(chosenNames()).toEqual(['photo.pdf']);
    });

    it('that cannot be used are refused with the reason, and the rest are kept', async () => {
      await pick([
        pdf('good.pdf'),
        new File(['x'], 'notes.txt'),
        pdf('huge.pdf', 6 * 1024 * 1024),
        new File([], 'empty.pdf'),
        pdf('good.pdf'),
      ]);

      expect(chosenNames()).toEqual(['good.pdf']);
      const problems = Array.from(el.querySelectorAll('[data-testid=file-problems] li')).map((li) => li.textContent?.trim());
      expect(problems).toEqual([
        '"notes.txt" is not a PDF, JPG or PNG file.',
        '"huge.pdf" is 6 MB, which is over the 5 MB limit.',
        '"empty.pdf" is empty.',
        '"good.pdf" is already in the list.',
      ]);
    });

    it('stop at five', async () => {
      await pick(['a', 'b', 'c', 'd', 'e', 'f'].map((n) => pdf(`${n}.pdf`)));

      expect(chosenNames()).toEqual(['a.pdf', 'b.pdf', 'c.pdf', 'd.pdf', 'e.pdf']);
      expect(el.querySelector('[data-testid=file-problems]')?.textContent).toContain('You can attach up to 5 documents');
    });

    it('are uploaded one after another once the claim exists, then the claimant lands on the claim with a confirmation', async () => {
      await fillValid();
      await pick([pdf('estimate.pdf'), pdf('photo.pdf')]);
      await submit();

      http.expectOne('/api/claims').flush(claim({ id: 99 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();
      expect(submitButton().textContent?.trim()).toBe('Uploading documents (0 of 2)…');

      const first = http.expectOne('/api/claims/99/documents');
      expect(first.request.method).toBe('POST');
      expect(((first.request.body as FormData).get('file') as File).name).toBe('estimate.pdf');
      http.expectNone('/api/claims/99/documents'); // the second waits for the first
      first.flush(claimDocument({ id: 1 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();
      expect(submitButton().textContent?.trim()).toBe('Uploading documents (1 of 2)…');

      const second = http.expectOne('/api/claims/99/documents');
      expect(((second.request.body as FormData).get('file') as File).name).toBe('photo.pdf');
      second.flush(claimDocument({ id: 2 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/claims', 99]);
      expect(TestBed.inject(Flash).take()).toEqual({ kind: 'success', text: 'Claim submitted with 2 documents.' });
    });

    it('that fail do not undo the claim: the others still go up and the claim page says what to retry', async () => {
      await fillValid();
      await pick([pdf('bad.pdf'), pdf('good.pdf'), pdf('offline.pdf')]);
      await submit();
      http.expectOne('/api/claims').flush(claim({ id: 99 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      http.expectOne('/api/claims/99/documents').flush({ detail: '"bad.pdf" is not a PDF, JPG or PNG file' }, { status: 415, statusText: 'Unsupported Media Type' });
      await fixture.whenStable();
      http.expectOne('/api/claims/99/documents').flush(claimDocument({ id: 2 }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();
      http.expectOne('/api/claims/99/documents').error(new ProgressEvent('error'));
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/claims', 99]);
      const flash = TestBed.inject(Flash).take();
      expect(flash?.kind).toBe('warning');
      expect(flash?.text).toContain('2 of 3 documents could not be attached');
      expect(flash?.text).toContain('"bad.pdf" is not a PDF, JPG or PNG file');
      expect(flash?.text).toContain('"offline.pdf": Cannot reach the server');
      expect(flash?.text).toContain('You can add them in the Documents section');
    });

    it('are kept in the list, and nothing is uploaded, when the claim itself is refused', async () => {
      await fillValid();
      await pick([pdf('estimate.pdf')]);
      await submit();
      http.expectOne('/api/claims').flush({ detail: 'Request validation failed', errors: { amountClaimed: 'must be greater than zero' } }, { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      http.expectNone('/api/claims/99/documents');
      expect(chosenNames()).toEqual(['estimate.pdf']);
      expect(navigate).not.toHaveBeenCalled();
    });
  });

  it('cannot be submitted empty, and a complete form is sent', async () => {
    expect(await attempt()).toBe(false);
    await fillValid();
    expect(await attempt()).toBe(true);
  });

  it('sends exactly what the backend expects: the generated policy number, trimmed text, upper-case market, the session email', async () => {
    await fillValid({ market: 'HK' });
    choose(el, '#claimType', 'PROPERTY');
    await fixture.whenStable();
    const generated = policy();
    await submit();

    const req = http.expectOne('/api/claims');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      policyNumber: generated,
      claimantName: 'Chen Wei',
      claimantEmail: 'claimant@example.com',
      incidentDate: daysAgo(2),
      description: 'Rear bumper damage from a parking collision.',
      amountClaimed: 1250.5,
      claimType: 'PROPERTY',
      market: 'HK',
    });
    expect(generated).toMatch(/^POL-\d{4}-\d{6}$/);
    req.flush(claim({ id: 99 }), { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/claims', 99]);
  });

  it('shows backend field errors beside the inputs and keeps the form editable', async () => {
    await fillValid();
    await submit();

    http.expectOne('/api/claims').flush(
      { detail: 'Request validation failed', errors: { policyNumber: 'must not be blank', amountClaimed: 'must be greater than zero' } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(fieldMessages()).toContain('must not be blank');
    expect(fieldMessages()).toContain('must be greater than zero');
    expect(el.querySelector('[role=alert]')?.textContent).toContain('correct the highlighted fields');
    expect(submitButton().disabled).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows a non-field backend error (e.g. 403) as a banner', async () => {
    await fillValid();
    await submit();

    http.expectOne('/api/claims').flush(
      { detail: 'Claimant email must match the authenticated account' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Claimant email must match the authenticated account');
  });
});
