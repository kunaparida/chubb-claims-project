import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Flash, FlashMessage } from '../../core/flash';
import { Claim, ClaimDocument, ClaimHistoryEntry, Role } from '../../core/models';
import { FileSaver } from '../../core/save-file';
import { buttonLabels, choose, claim, claimDocument, historyEntry, storeSession, type } from '../../testing';
import { ClaimDetail } from './claim-detail';

describe('ClaimDetail', () => {
  let fixture: ComponentFixture<ClaimDetail>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const history: ClaimHistoryEntry[] = [
    historyEntry('SUBMITTED', { id: 1 }),
    historyEntry('UNDER_REVIEW', { id: 2, fromStatus: 'SUBMITTED', changedByName: 'Priya Nair', note: null }),
  ];

  const saved: { blob: Blob; name: string }[] = [];

  /** Opens the page as `role`, answering the claim, history and documents requests the page makes on load. */
  const open = async (role: Role, c: Claim, hist: ClaimHistoryEntry[] = history, docs: ClaimDocument[] = [], flash?: FlashMessage) => {
    storeSession(role);
    saved.length = 0;
    TestBed.configureTestingModule({
      imports: [ClaimDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FileSaver, useValue: { save: (blob: Blob, name: string) => saved.push({ blob, name }) } },
      ],
    });
    if (flash) {
      TestBed.inject(Flash).set(flash);
    }
    fixture = TestBed.createComponent(ClaimDetail);
    el = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('id', String(c.id));
    await fixture.whenStable();
    http.expectOne(`/api/claims/${c.id}`).flush(c);
    http.expectOne(`/api/claims/${c.id}/history`).flush(hist);
    http.expectOne(`/api/claims/${c.id}/documents`).flush(docs);
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

  describe('documents', () => {
    const pdf = (name = 'estimate.pdf', size = 1000) => new File([new Uint8Array(size)], name, { type: 'application/pdf' });
    const settle = async () => {
      for (let i = 0; i < 3; i++) {
        await new Promise((resolve) => setTimeout(resolve, 10)); // lets the FileReader and promise callbacks run
        await fixture.whenStable();
      }
    };
    const pick = async (files: File[]) => {
      const input = el.querySelector<HTMLInputElement>('input[type=file]') as HTMLInputElement;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      await fixture.whenStable();
    };
    const uploadButton = () =>
      Array.from(el.querySelectorAll<HTMLButtonElement>('button.primary')).find((b) => b.textContent?.trim().startsWith('Upload')) as HTMLButtonElement;
    const names = (testId: string) => Array.from(el.querySelectorAll(`[data-testid=${testId}] .file-name`)).map((n) => n.textContent?.trim());

    it('lists each document with its size, who added it and when', async () => {
      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }), history, [
        claimDocument({ id: 1 }),
        claimDocument({ id: 2, fileName: 'photo.jpg', contentType: 'image/jpeg', sizeBytes: 2_500_000, uploadedByName: 'Priya Nair' }),
      ]);

      expect(names('documents')).toEqual(['repair-estimate.pdf', 'photo.jpg']);
      const rows = Array.from(el.querySelectorAll('[data-testid=documents] li')).map((li) => li.textContent ?? '');
      expect(rows[0]).toContain('180 KB');
      expect(rows[0]).toContain('Chen Wei');
      expect(rows[1]).toContain('2.4 MB');
      expect(rows[1]).toContain('Priya Nair');
      expect(el.querySelector('.documents h2 .chip')?.textContent).toBe('2');
      expect(el.querySelector('[data-testid=no-documents]')).toBeNull();
    });

    it('says so when there are none', async () => {
      await open('MANAGER', claim({ status: 'SUBMITTED' }));
      http.expectOne('/api/dashboard/performance').flush([]);
      await fixture.whenStable();

      expect(el.querySelector('[data-testid=no-documents]')?.textContent).toContain('No documents');
      expect(el.querySelector('[data-testid=documents]')).toBeNull();
    });

    it('is offered for adding only to the claimant who owns an open claim', async () => {
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }));
      expect(el.querySelector('input[type=file]')).not.toBeNull();
      TestBed.resetTestingModule();

      await open('CLAIMANT', claim({ status: 'REJECTED' }));
      expect(el.querySelector('input[type=file]')).toBeNull();
      TestBed.resetTestingModule();

      await open('CLAIMANT', claim({ status: 'UNDER_REVIEW', claimantId: 99 }));
      expect(el.querySelector('input[type=file]')).toBeNull();
      TestBed.resetTestingModule();

      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }));
      expect(el.querySelector('input[type=file]')).toBeNull();
    });

    it('downloads a document through the API (so the token is sent) and hands it to the browser to save', async () => {
      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }), history, [claimDocument({ id: 5, fileName: 'estimate.pdf' })]);
      el.querySelector<HTMLButtonElement>('[data-testid=documents] button')?.click();
      await fixture.whenStable();

      const req = http.expectOne('/api/claims/42/documents/5');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      const blob = new Blob(['%PDF-1.4']);
      req.flush(blob);
      await fixture.whenStable();

      expect(saved).toHaveLength(1);
      expect(saved[0].name).toBe('estimate.pdf');
      expect(saved[0].blob).toBe(blob);
    });

    it('explains a failed download with the reason the backend gave', async () => {
      await open('OFFICER', claim({ status: 'UNDER_REVIEW', assignedOfficerId: 7 }), history, [claimDocument({ id: 5 })]);
      el.querySelector<HTMLButtonElement>('[data-testid=documents] button')?.click();
      await fixture.whenStable();
      http.expectOne('/api/claims/42/documents/5').flush(new Blob([JSON.stringify({ detail: 'Document not found' })]), { status: 404, statusText: 'Not Found' });
      await settle();

      expect(el.querySelector('[data-testid=document-problems]')?.textContent).toContain('Document not found');
      expect(saved).toHaveLength(0);
    });

    it('lets the claimant pick files, explains any that cannot be used, uploads the rest and shows the new list', async () => {
      await open('CLAIMANT', claim({ status: 'MORE_INFO_REQUIRED', informationRequest: 'Send the estimate' }));
      await pick([pdf('estimate.pdf'), new File(['x'], 'notes.txt')]);

      expect(names('new-files')).toEqual(['estimate.pdf']);
      expect(el.querySelector('[data-testid=document-problems]')?.textContent).toContain('"notes.txt" is not a PDF, JPG or PNG file.');
      expect(uploadButton().textContent?.trim()).toBe('Upload 1 document');

      uploadButton().click();
      await fixture.whenStable();
      const upload = http.expectOne('/api/claims/42/documents');
      expect(upload.request.method).toBe('POST');
      expect((upload.request.body as FormData).get('file')).toBeInstanceOf(File);
      upload.flush(claimDocument({ id: 9, fileName: 'estimate.pdf' }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();
      http.expectOne('/api/claims/42/documents').flush([claimDocument({ id: 9, fileName: 'estimate.pdf' })]);
      await fixture.whenStable();

      expect(el.querySelector('[role=status]')?.textContent).toContain('1 document attached.');
      expect(names('documents')).toEqual(['estimate.pdf']);
      expect(el.querySelector('[data-testid=new-files]')).toBeNull();
      expect(el.querySelector('[data-testid=document-problems]')).toBeNull();
    });

    it('reports a file the backend refuses and still attaches the others', async () => {
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }));
      await pick([pdf('bad.pdf'), pdf('good.pdf')]);
      uploadButton().click();
      await fixture.whenStable();

      http.expectOne('/api/claims/42/documents').flush({ detail: '"bad.pdf" is not a PDF, JPG or PNG file' }, { status: 415, statusText: 'Unsupported Media Type' });
      await fixture.whenStable();
      http.expectOne('/api/claims/42/documents').flush(claimDocument({ id: 2, fileName: 'good.pdf' }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();
      http.expectOne('/api/claims/42/documents').flush([claimDocument({ id: 2, fileName: 'good.pdf' })]);
      await fixture.whenStable();

      expect(el.querySelector('[data-testid=document-problems]')?.textContent).toContain('"bad.pdf" is not a PDF, JPG or PNG file');
      expect(el.querySelector('[role=status]')?.textContent).toContain('1 document attached.');
      expect(names('documents')).toEqual(['good.pdf']);
    });

    it('lets a chosen file be removed again before uploading', async () => {
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }));
      await pick([pdf('a.pdf'), pdf('b.pdf')]);
      expect(names('new-files')).toEqual(['a.pdf', 'b.pdf']);

      el.querySelector<HTMLButtonElement>('[data-testid=new-files] button')?.click();
      await fixture.whenStable();

      expect(names('new-files')).toEqual(['b.pdf']);
    });

    it('stops offering more once the claim has the maximum of five', async () => {
      const five = [1, 2, 3, 4, 5].map((id) => claimDocument({ id, fileName: `doc-${id}.pdf` }));
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }), history, five);

      expect(el.querySelector('input[type=file]')).toBeNull();
      expect(el.querySelector('.add-documents')?.textContent).toContain('maximum of 5 documents');
    });

    it('does not count documents already on the claim as free room when choosing more', async () => {
      const four = [1, 2, 3, 4].map((id) => claimDocument({ id, fileName: `doc-${id}.pdf` }));
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }), history, four);
      await pick([pdf('five.pdf'), pdf('six.pdf')]);

      expect(names('new-files')).toEqual(['five.pdf']);
      expect(el.querySelector('[data-testid=document-problems]')?.textContent).toContain('up to 5 documents');
    });

    it('shows the result of the uploads made while the claim was being submitted, once', async () => {
      await open('CLAIMANT', claim({ status: 'SUBMITTED' }), history, [claimDocument()], { kind: 'success', text: 'Claim submitted with 1 document.' });
      expect(el.querySelector('[role=status]')?.textContent).toContain('Claim submitted with 1 document.');
      expect(TestBed.inject(Flash).take()).toBeNull();
      TestBed.resetTestingModule();

      await open('CLAIMANT', claim({ status: 'SUBMITTED' }), history, [], { kind: 'warning', text: 'Your claim was submitted, but 1 of 2 documents could not be attached.' });
      expect(el.querySelector('[role=alert]')?.textContent).toContain('could not be attached');
    });
  });});
