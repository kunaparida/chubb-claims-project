import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Role } from '../../core/models';
import { choose, claim, page, storeSession } from '../../testing';
import { ClaimsList } from './claims-list';

describe('ClaimsList', () => {
  let fixture: ComponentFixture<ClaimsList>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const open = async (role: Role) => {
    storeSession(role);
    TestBed.configureTestingModule({
      imports: [ClaimsList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ClaimsList);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  };
  const list = () => http.expectOne((r) => r.url === '/api/claims');

  afterEach(() => localStorage.clear());

  it.each([
    ['CLAIMANT', 'My claims'],
    ['OFFICER', 'Assigned to me'],
    ['MANAGER', 'All claims'],
  ] as [Role, string][])('%s sees the heading "%s" (the server scopes the data)', async (role, heading) => {
    await open(role);
    list().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toBe(heading);
  });

  it('only claimants are offered "Report an incident"', async () => {
    await open('CLAIMANT');
    list().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.textContent).toContain('Report an incident');
    TestBed.resetTestingModule();
    localStorage.clear();

    await open('OFFICER');
    list().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Report an incident');
  });

  it('shows each claim with its status, amount and market, and flags what needs the claimant', async () => {
    await open('CLAIMANT');
    list().flush(page([
      claim({ id: 1, claimNumber: 'CLM-1', status: 'MORE_INFO_REQUIRED', amountClaimed: 12000, market: 'ID' }),
      claim({ id: 2, claimNumber: 'CLM-2', status: 'APPROVED' }),
    ]));
    await fixture.whenStable();

    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('CLM-1');
    expect(rows[0].textContent).toContain('More information required');
    expect(rows[0].textContent).toContain('Action needed');
    expect(rows[0].textContent).toContain('12,000.00');
    expect(rows[0].textContent).toContain('ID');
    expect(rows[1].textContent).not.toContain('Action needed');
  });

  it('requests the first page of 10 and filters by status on the server, back to page 0', async () => {
    await open('MANAGER');
    const first = list();
    expect(first.request.params.get('page')).toBe('0');
    expect(first.request.params.get('size')).toBe('10');
    expect(first.request.params.has('status')).toBe(false);
    first.flush(page([claim()]));
    await fixture.whenStable();

    choose(el, '#status', 'APPROVED');
    const filtered = list();
    expect(filtered.request.params.get('status')).toBe('APPROVED');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush(page([]));
    await fixture.whenStable();
    expect(el.textContent).toContain('No claims to show');
  });

  it('pages through results with the Next and Previous buttons', async () => {
    await open('MANAGER');
    list().flush(page([claim()], { page: 0, totalPages: 3, totalElements: 25, first: true, last: false }));
    await fixture.whenStable();
    const [previous, next] = Array.from(el.querySelectorAll<HTMLButtonElement>('.pager button'));
    expect(previous.disabled).toBe(true);

    next.click();
    const second = list();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([claim()], { page: 1, totalPages: 3, totalElements: 25, first: false, last: false }));
    await fixture.whenStable();
    expect(el.textContent).toContain('Page 2 of 3');
  });

  it('shows an error with a retry rather than a blank page', async () => {
    await open('CLAIMANT');
    list().flush({ detail: 'An unexpected error occurred' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('An unexpected error occurred');

    el.querySelector<HTMLButtonElement>('[role=alert] button')?.click();
    list().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')).toBeNull();
    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
  });
});
