import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Liability, OfficerPerformance, Workload } from '../../core/models';
import { storeSession } from '../../testing';
import { Dashboard } from './dashboard';

const workload: Workload = {
  unassignedClaims: 3, totalOpenClaims: 9, submitted: 3, underReview: 4, awaitingInformation: 1, approved: 1, rejected: 2, settled: 5,
};
const liability: Liability = {
  pendingExposure: 20800,
  approvedAwaitingSettlement: 9500,
  byClaimType: [
    { key: 'MOTOR', pendingExposure: 4800, approvedAwaitingSettlement: 9500 },
    { key: 'PROPERTY', pendingExposure: 16000, approvedAwaitingSettlement: 0 },
  ],
  byMarket: [
    { key: 'HK', pendingExposure: 16000, approvedAwaitingSettlement: 9500 },
    { key: 'SG', pendingExposure: 4800, approvedAwaitingSettlement: 0 },
  ],
};
const performance: OfficerPerformance[] = [
  { officerId: 7, officerName: 'Priya Nair', assignedClaims: 6, activeClaims: 3, resolvedClaims: 3 },
  { officerId: 9, officerName: 'Tomas Lim', assignedClaims: 0, activeClaims: 0, resolvedClaims: 0 },
];

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const open = async () => {
    storeSession('MANAGER');
    TestBed.configureTestingModule({ imports: [Dashboard], providers: [provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(Dashboard);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  };
  const flushAll = async () => {
    http.expectOne('/api/dashboard/workload').flush(workload);
    http.expectOne('/api/dashboard/liability').flush(liability);
    http.expectOne('/api/dashboard/performance').flush(performance);
    await fixture.whenStable();
  };

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows the queue and open counts and every status', async () => {
    await open();
    await flushAll();

    expect(el.querySelector('[data-testid=unassigned]')?.textContent).toBe('3');
    expect(el.querySelector('[data-testid=open]')?.textContent).toBe('9');
    for (const label of ['Submitted', 'Under review', 'Awaiting information', 'Approved, unpaid', 'Rejected', 'Settled']) {
      expect(el.textContent).toContain(label);
    }
  });

  it('shows liability per market first and labels the grand total as unconverted', async () => {
    await open();
    await flushAll();

    const marketRows = Array.from(el.querySelectorAll('[data-testid=by-market] tbody tr')).map((r) => r.textContent ?? '');
    expect(marketRows).toHaveLength(2);
    expect(marketRows[0]).toContain('HK');
    expect(marketRows[0]).toContain('16,000.00');
    expect(marketRows[1]).toContain('SG');

    const total = el.querySelector('[data-testid=grand-total]')?.textContent ?? '';
    expect(total).toContain('20,800.00');
    expect(total).toContain('unconverted, mixed markets'); // no currency in the backend: never present this as one currency
    expect(el.textContent).not.toMatch(/\b(SGD|HKD|USD|\$)\b/);
  });

  it('breaks liability down by line of business', async () => {
    await open();
    await flushAll();
    const rows = Array.from(el.querySelectorAll('[data-testid=by-type] tbody tr')).map((r) => r.textContent ?? '');
    expect(rows[0]).toContain('Motor');
    expect(rows[0]).toContain('9,500.00');
    expect(rows[1]).toContain('Property');
  });

  it('lists every officer, including one with no claims', async () => {
    await open();
    await flushAll();
    const rows = Array.from(el.querySelectorAll('[data-testid=officers] tbody tr')).map((r) => r.textContent ?? '');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('Priya Nair');
    expect(rows[1]).toContain('Tomas Lim');
  });

  it('with no open claims shows empty tables instead of errors', async () => {
    await open();
    http.expectOne('/api/dashboard/workload').flush({ ...workload, unassignedClaims: 0, totalOpenClaims: 0 });
    http.expectOne('/api/dashboard/liability').flush({ pendingExposure: 0, approvedAwaitingSettlement: 0, byClaimType: [], byMarket: [] });
    http.expectOne('/api/dashboard/performance').flush([]);
    await fixture.whenStable();

    expect(el.textContent).toContain('No open claims.');
    expect(el.textContent).toContain('No officers.');
  });

  it('shows an error, and Refresh retries', async () => {
    await open();
    http.expectOne('/api/dashboard/workload').flush({ detail: 'Manager access is required' }, { status: 403, statusText: 'Forbidden' });
    http.match('/api/dashboard/liability');
    http.match('/api/dashboard/performance');
    await fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Manager access is required');

    el.querySelector<HTMLButtonElement>('.page-head button')?.click();
    await fixture.whenStable();
    await flushAll();
    expect(el.querySelector('[role=alert]')).toBeNull();
    expect(el.querySelector('[data-testid=open]')?.textContent).toBe('9');
  });
});
