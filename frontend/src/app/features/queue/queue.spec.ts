import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Role } from '../../core/models';
import { claim, page, storeSession } from '../../testing';
import { Queue } from './queue';

describe('Queue', () => {
  let fixture: ComponentFixture<Queue>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const open = async (role: Role) => {
    storeSession(role);
    TestBed.configureTestingModule({
      imports: [Queue],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(Queue);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  };
  const queue = () => http.expectOne((r) => r.url === '/api/claims/queue');

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('lists the waiting claims in the order the server gives (oldest first) with amount and market', async () => {
    await open('OFFICER');
    const req = queue();
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([
      claim({ id: 1, claimNumber: 'CLM-OLD', amountClaimed: 5000, market: 'HK' }),
      claim({ id: 2, claimNumber: 'CLM-NEW' }),
    ]));
    await fixture.whenStable();

    const rows = Array.from(el.querySelectorAll('tbody tr')).map((r) => r.textContent ?? '');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('CLM-OLD');
    expect(rows[0]).toContain('5,000.00');
    expect(rows[0]).toContain('HK');
    expect(rows[1]).toContain('CLM-NEW');
  });

  it('an officer can pick a claim up; a manager cannot (the backend allows only officers)', async () => {
    await open('OFFICER');
    queue().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.querySelector('tbody button')?.textContent?.trim()).toBe('Pick up');
    TestBed.resetTestingModule();
    localStorage.clear();

    await open('MANAGER');
    queue().flush(page([claim()]));
    await fixture.whenStable();
    expect(el.querySelector('tbody button')).toBeNull();
    expect(el.querySelector('tbody a')?.textContent).toBe('CLM-2026-0042');
  });

  it('picking up sends PUT /pickup, tells the officer, and refreshes the queue', async () => {
    await open('OFFICER');
    queue().flush(page([claim({ id: 42 })]));
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('tbody button')?.click();
    await fixture.whenStable();
    const pick = http.expectOne('/api/claims/42/pickup');
    expect(pick.request.method).toBe('PUT');
    pick.flush(claim({ id: 42, assignedOfficerId: 7 }));
    queue().flush(page([]));
    await fixture.whenStable();

    expect(el.querySelector('[role=status]')?.textContent).toContain('You picked up CLM-2026-0042');
    expect(el.querySelector('[role=status] a')?.getAttribute('href')).toBe('/claims/42');
    expect(el.textContent).toContain('The queue is empty');
  });

  it('losing the race shows the server reason and the queue as it now is', async () => {
    await open('OFFICER');
    queue().flush(page([claim({ id: 42 })]));
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('tbody button')?.click();
    await fixture.whenStable();
    http.expectOne('/api/claims/42/pickup').flush(
      { detail: 'Claim is already assigned to another officer' }, { status: 409, statusText: 'Conflict' });
    queue().flush(page([]));
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('already assigned to another officer');
    expect(el.querySelector('tbody')).toBeNull();
  });

  it('pages with the server page numbers', async () => {
    await open('OFFICER');
    queue().flush(page([claim()], { totalPages: 2, totalElements: 14, first: true, last: false }));
    await fixture.whenStable();

    el.querySelectorAll<HTMLButtonElement>('.pager button')[1].click();
    const second = queue();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page([claim()], { page: 1, totalPages: 2, totalElements: 14, first: false, last: true }));
    await fixture.whenStable();
    expect(el.textContent).toContain('Page 2 of 2');
  });

  it('shows an error when the queue cannot be loaded', async () => {
    await open('OFFICER');
    queue().flush({ detail: 'Officer or manager access is required' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Officer or manager access is required');
  });
});
