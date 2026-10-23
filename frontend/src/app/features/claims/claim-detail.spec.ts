import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Claim, ClaimHistoryEntry, Role } from '../../core/models';
import { buttonLabels, choose, claim, historyEntry, storeSession, type } from '../../testing';
import { ClaimDetail } from './claim-detail';

describe('ClaimDetail', () => {
  let fixture: ComponentFixture<ClaimDetail>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const history: ClaimHistoryEntry[] = [
    historyEntry('SUBMITTED', { id: 1 }),
    historyEntry('UNDER_REVIEW', { id: 2, fromStatus: 'SUBMITTED', changedByName: 'Priya Nair', note: null }),
  ];

  /** Opens the page as `role`, answering the claim + history requests the page makes on load. */
  const open = async (role: Role, c: Claim, hist: ClaimHistoryEntry[] = history) => {
    storeSession(role);
    TestBed.configureTestingModule({
      imports: [ClaimDetail],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ClaimDetail);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', String(c.id));
    await fixture.whenStable();
    http.expectOne(`/api/claims/${c.id}`).flush(c);
    http.expectOne(`/api/claims/${c.id}/history`).flush(hist);
    await fixture.whenStable();
  };
  const actionButtons = () => buttonLabels(el, '.actions button');
  const clickAction = async (label: string) => {
    const button = Array.from(el.querySelectorAll<HTMLButtonElement>('.actions button')).find((b) => b.textContent?.trim() === label);
    if (!button) {
      throw new Error(`no action button "${label}" among: ${actionButtons().join(', ')}`);
    }
    button.click();
    await fixture.whenStable();
  };

  afterEach(() => {
    http.verify({ ignoreCancelled: true }); // forkJoin cancels the sibling request when one of the two fails
    localStorage.clear();
  });

  describe('the buttons offered match the central action table', () => {
    const cases: [string, Role, Partial<Claim>, string[]][] = [
      ['claimant while information is required', 'CLAIMANT', { status: 'MORE_INFO_REQUIRED', informationRequest: 'Send the estimate' }, ['Send information']],
      ['claimant on a submitted claim', 'CLAIMANT', { status: 'SUBMITTED' }, []],
      ['claimant on an approved claim', 'CLAIMANT', { status: 'APPROVED' }, []],
      ['officer on an unassigned submitted claim', 'OFFICER', { status: 'SUBMITTED' }, ['Pick up this claim']],
      ['officer on a claim they picked up', 'OFFICER', { status: 'SUBMITTED', assignedOfficerId: 7 }, ['Start review']],
      ['officer on a claim under review', 'OFFICER', { status: 'UNDER_REVIEW', assignedOfficerId: 7 }, ['Request information', 'Approve', 'Reject']],
      ['officer on an approved claim', 'OFFICER', { status: 'APPROVED', assignedOfficerId: 7 }, ['Mark as settled']],
      ['officer on a rejected claim', 'OFFICER', { status: 'REJECTED', assignedOfficerId: 7 }, []],
      ['officer on someone else\'s claim', 'OFFICER', { status: 'UNDER_REVIEW', assignedOfficerId: 9 }, []],
    ];

    for (const [label, role, over, expected] of cases) {
      it(label, async () => {
        await open(role, claim(over));
        expect(actionButtons()).toEqual(expected);
        expect(!!el.querySelector('[data-testid=no-actions]')).toBe(expected.length === 0);
      });
    }

    it('a manager can assign and act on a claim under review (officers come from the performance list)', async () => {
      await open('MANAGER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 9, assignedOfficerName: 'Tomas Lim' }));
      http.expectOne('/api/dashboard/performance').flush([
        { officerId: 9, officerName: 'Tomas Lim', assignedClaims: 3, activeClaims: 2, resolvedClaims: 1 },
      ]);
      await fixture.whenStable();

      expect(actionButtons()).toEqual(['Assign to an officer', 'Request information', 'Approve', 'Reject']);
      expect(el.querySelector('label[for=assignee]')?.textContent).toBe('Reassign to');
      expect(el.querySelector('#assignee')?.textContent).toContain('Tomas Lim (2 active)');
    });

    it('a manager is offered nothing on a closed claim, and the officer list is not even requested', async () => {
      await open('MANAGER', claim({ status: 'SETTLED', assignedOfficerId: 9 }));
      expect(actionButtons()).toEqual([]);
      http.expectNone('/api/dashboard/performance');
    });
  });

  it('shows the claim, the amount with its market, the decision, the information exchange and the history', async () => {
    await open(
      'OFFICER',
      claim({
        status: 'APPROVED',
        assignedOfficerId: 7,
        assignedOfficerName: 'Priya Nair',
        amountClaimed: 1250.5,
        market: 'SG',
        decisionNotes: 'Estimate verified.',
        informationRequest: 'Please send the estimate.',
        informationResponse: 'Estimate: 1,200.',
      }),
      [historyEntry('SUBMITTED'), historyEntry('APPROVED', { id: 3, fromStatus: 'UNDER_REVIEW', changedByName: 'Priya Nair', note: 'Estimate verified.' })],
    );

    expect(el.querySelector('h1')?.textContent).toBe('CLM-2026-0042');
    expect(el.querySelector('[data-testid=amount]')?.textContent).toContain('1,250.50');
    expect(el.querySelector('[data-testid=amount]')?.textContent).toContain('SG');
    expect(el.querySelector('[data-testid=decision]')?.textContent).toBe('Estimate verified.');
    expect(el.querySelector('[data-testid=info-request]')?.textContent).toBe('Please send the estimate.');
    expect(el.querySelector('[data-testid=info-response]')?.textContent).toBe('Estimate: 1,200.');
    const steps = Array.from(el.querySelectorAll('[data-testid=history] li')).map((li) => li.textContent);
    expect(steps).toHaveLength(2);
    expect(steps[1]).toContain('Under review → Approved');
    expect(steps[1]).toContain('Priya Nair');
  });

  it('says it is waiting for the claimant while a question is unanswered', async () => {
    await open('OFFICER', claim({ status: 'MORE_INFO_REQUIRED', assignedOfficerId: 7, informationRequest: 'Send the estimate' }));
    expect(el.textContent).toContain('Waiting for the claimant to answer');
  });

  describe('commands', () => {
    it('pick up -> PUT /pickup, then the page shows the returned claim and offers "Start review"', async () => {
      await open('OFFICER', claim({ status: 'SUBMITTED' }));
      await clickAction('Pick up this claim');

      const req = http.expectOne('/api/claims/42/pickup');
      expect(req.request.method).toBe('PUT');
      req.flush(claim({ status: 'SUBMITTED', assignedOfficerId: 7, assignedOfficerName: 'Priya Nair' }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.querySelector('[role=status]')?.textContent).toContain('You picked up this claim');
      expect(actionButtons()).toEqual(['Start review']);
    });

    it('a note is required before approve/reject can be sent, and approve sends it', async () => {
      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }));
      const disabled = () => Array.from(el.querySelectorAll<HTMLButtonElement>('.actions button')).map((b) => b.disabled);
      expect(disabled()).toEqual([true, true, true]);

      type(el, '#note', '   ');
      await fixture.whenStable();
      expect(disabled()).toEqual([true, true, true]); // blank is not a note

      type(el, '#note', 'Estimate verified.');
      await fixture.whenStable();
      expect(disabled()).toEqual([false, false, false]);

      await clickAction('Approve');
      const req = http.expectOne('/api/claims/42/approve');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ note: 'Estimate verified.' });
      req.flush(claim({ status: 'APPROVED', assignedOfficerId: 7, decisionNotes: 'Estimate verified.' }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.querySelector('app-status-badge')?.textContent).toContain('Approved');
      expect(actionButtons()).toEqual(['Mark as settled']);
      expect((el.querySelector('#note') as HTMLTextAreaElement | null)?.value ?? '').toBe(''); // cleared after success
    });

    it('request information, reject and settle each send their note to their own endpoint', async () => {
      const send = async (status: Claim['status'], button: string, path: string) => {
        TestBed.resetTestingModule();
        await open('OFFICER', claim({ status, assignedOfficerId: 7 }));
        type(el, '#note', 'because');
        await fixture.whenStable();
        await clickAction(button);
        const req = http.expectOne(`/api/claims/42/${path}`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ note: 'because' });
        req.flush(claim({ status, assignedOfficerId: 7 }));
        http.expectOne('/api/claims/42/history').flush(history);
        await fixture.whenStable();
      };
      await send('UNDER_REVIEW', 'Request information', 'request-info');
      await send('UNDER_REVIEW', 'Reject', 'reject');
      await send('APPROVED', 'Mark as settled', 'settle');
    });

    it('the claimant answers the officer: PUT /provide-info with the response', async () => {
      await open('CLAIMANT', claim({ status: 'MORE_INFO_REQUIRED', assignedOfficerId: 7, informationRequest: 'Send the estimate' }));
      expect(el.querySelector<HTMLButtonElement>('.actions button')?.disabled).toBe(true);

      type(el, '#response', 'Estimate: 1,200 at AutoFix.');
      await fixture.whenStable();
      await clickAction('Send information');

      const req = http.expectOne('/api/claims/42/provide-info');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ response: 'Estimate: 1,200 at AutoFix.' });
      req.flush(claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7, informationRequest: 'Send the estimate', informationResponse: 'Estimate: 1,200 at AutoFix.' }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.querySelector('[role=status]')?.textContent).toContain('Thank you');
      expect(actionButtons()).toEqual([]);
      expect(el.querySelector('[data-testid=info-response]')?.textContent).toContain('AutoFix');
    });

    it('a manager picks an officer and assigns: PUT /assign {officerId}', async () => {
      await open('MANAGER', claim({ status: 'SUBMITTED' }));
      http.expectOne('/api/dashboard/performance').flush([
        { officerId: 7, officerName: 'Priya Nair', assignedClaims: 1, activeClaims: 1, resolvedClaims: 0 },
        { officerId: 9, officerName: 'Tomas Lim', assignedClaims: 0, activeClaims: 0, resolvedClaims: 0 },
      ]);
      await fixture.whenStable();

      const assign = () => Array.from(el.querySelectorAll<HTMLButtonElement>('.actions button')).find((b) => b.textContent?.trim() === 'Assign to an officer') as HTMLButtonElement;
      expect(assign().disabled).toBe(true); // nobody chosen yet
      choose(el, '#assignee', '9');
      await fixture.whenStable();
      expect(assign().disabled).toBe(false);
      assign().click();
      await fixture.whenStable();

      const req = http.expectOne('/api/claims/42/assign');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ officerId: 9 });
      req.flush(claim({ status: 'SUBMITTED', assignedOfficerId: 9, assignedOfficerName: 'Tomas Lim' }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.textContent).toContain('Claim assigned.');
      expect(el.querySelector('label[for=assignee]')?.textContent).toBe('Reassign to');
    });
  });

  describe('when the server disagrees', () => {
    it('shows the server reason and reloads, so a lost pick-up race ends with the true state and no stale button', async () => {
      await open('OFFICER', claim({ status: 'SUBMITTED' }));
      await clickAction('Pick up this claim');

      http.expectOne('/api/claims/42/pickup').flush(
        { detail: 'Claim is already assigned to another officer' }, { status: 409, statusText: 'Conflict' });
      // the page re-reads the claim: someone else owns it now
      http.expectOne('/api/claims/42').flush(claim({ status: 'SUBMITTED', assignedOfficerId: 9, assignedOfficerName: 'Tomas Lim' }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.querySelector('[role=alert]')?.textContent).toContain('already assigned to another officer');
      expect(actionButtons()).toEqual([]);
    });

    it('re-enables the buttons after a failure so the user can retry', async () => {
      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }));
      type(el, '#note', 'ok');
      await fixture.whenStable();
      await clickAction('Approve');
      http.expectOne('/api/claims/42/approve').flush({ detail: 'Request body is missing or malformed' }, { status: 400, statusText: 'Bad Request' });
      http.expectOne('/api/claims/42').flush(claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }));
      http.expectOne('/api/claims/42/history').flush(history);
      await fixture.whenStable();

      expect(el.querySelector('[role=alert]')).not.toBeNull();
      expect(el.querySelector<HTMLButtonElement>('.actions button')?.disabled).toBe(false);
    });

    it('a claim that cannot be loaded (404: not found or not yours) shows a message, not a blank page', async () => {
      storeSession('CLAIMANT');
      TestBed.configureTestingModule({
        imports: [ClaimDetail],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      fixture = TestBed.createComponent(ClaimDetail);
      el = fixture.nativeElement;
      http = TestBed.inject(HttpTestingController);
      fixture.componentRef.setInput('id', '999');
      await fixture.whenStable();
      http.expectOne('/api/claims/999').flush({ detail: 'Claim not found' }, { status: 404, statusText: 'Not Found' });
      // forkJoin cancels the sibling history request as soon as the claim request fails, so there is nothing left to flush
      await fixture.whenStable();

      expect(el.querySelector('[role=alert]')?.textContent).toContain('Claim not found');
      expect(el.querySelector('h1')).toBeNull();
    });
  });
});
