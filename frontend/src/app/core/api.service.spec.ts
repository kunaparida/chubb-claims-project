import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

/**
 * The integration contract, pinned: every method must use the exact verb, path and body the backend's controllers declare
 * (verified against its live OpenAPI document). A typo here means a 404/405 in production.
 */
describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const cases: [string, (a: ApiService) => unknown, string, string, unknown][] = [
    ['login', (a) => a.login({ email: 'a@b.c', password: 'pw' }), 'POST', '/api/auth/login', { email: 'a@b.c', password: 'pw' }],
    ['register', (a) => a.register({ email: 'a@b.c', password: 'pw', fullName: 'A B' }), 'POST', '/api/auth/register', { email: 'a@b.c', password: 'pw', fullName: 'A B' }],
    ['me', (a) => a.me(), 'GET', '/api/auth/me', null],
    ['getClaim', (a) => a.getClaim(42), 'GET', '/api/claims/42', null],
    ['history', (a) => a.history(42), 'GET', '/api/claims/42/history', null],
    ['pickup', (a) => a.pickup(42), 'PUT', '/api/claims/42/pickup', null],
    ['assign', (a) => a.assign(42, 7), 'PUT', '/api/claims/42/assign', { officerId: 7 }],
    ['review', (a) => a.review(42), 'PUT', '/api/claims/42/review', null],
    ['requestInfo', (a) => a.requestInfo(42, 'Need the estimate'), 'PUT', '/api/claims/42/request-info', { note: 'Need the estimate' }],
    ['provideInfo', (a) => a.provideInfo(42, 'Here it is'), 'PUT', '/api/claims/42/provide-info', { response: 'Here it is' }],
    ['approve', (a) => a.approve(42, 'ok'), 'PUT', '/api/claims/42/approve', { note: 'ok' }],
    ['reject', (a) => a.reject(42, 'no'), 'PUT', '/api/claims/42/reject', { note: 'no' }],
    ['settle', (a) => a.settle(42, 'paid'), 'PUT', '/api/claims/42/settle', { note: 'paid' }],
    ['workload', (a) => a.workload(), 'GET', '/api/dashboard/workload', null],
    ['liability', (a) => a.liability(), 'GET', '/api/dashboard/liability', null],
    ['performance', (a) => a.performance(), 'GET', '/api/dashboard/performance', null],
  ];

  for (const [name, call, method, url, body] of cases) {
    it(`${name} -> ${method} ${url}`, () => {
      (call(api) as { subscribe: () => void }).subscribe();
      const req = http.expectOne(url);
      expect(req.request.method).toBe(method);
      expect(req.request.body).toEqual(body);
      req.flush({});
    });
  }

  it('createClaim -> POST /api/claims with the full body', () => {
    const body = {
      policyNumber: 'POL-1', claimantName: 'Chen', claimantEmail: 'c@x.io', incidentDate: '2026-10-10',
      description: 'A description that is long enough.', amountClaimed: 10, claimType: 'MOTOR' as const, market: 'SG',
    };
    api.createClaim(body).subscribe();
    const req = http.expectOne('/api/claims');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('listClaims sends page, size and status as query parameters, omitting empty ones', () => {
    api.listClaims({ page: 2, size: 10, status: 'APPROVED' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/claims');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe('APPROVED');
    req.flush({});

    api.listClaims({ status: '' }).subscribe();
    const bare = http.expectOne((r) => r.url === '/api/claims');
    expect(bare.request.params.keys()).toEqual([]);
    bare.flush({});
  });

  it('documents -> GET /api/claims/{id}/documents', () => {
    api.documents(42).subscribe();
    const req = http.expectOne('/api/claims/42/documents');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('uploadDocument -> POST multipart/form-data with the file in the "file" field', () => {
    const file = new File(['%PDF-1.4'], 'estimate.pdf', { type: 'application/pdf' });
    api.uploadDocument(42, file).subscribe();
    const req = http.expectOne('/api/claims/42/documents');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(Array.from(body.keys())).toEqual(['file']);
    expect((body.get('file') as File).name).toBe('estimate.pdf');
    req.flush({});
  });

  it('downloadDocument -> GET the file as a blob (so the bearer token can be sent)', () => {
    api.downloadDocument(42, 7).subscribe();
    const req = http.expectOne('/api/claims/42/documents/7');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));
  });

  it('queue -> GET /api/claims/queue with paging', () => {
    api.queue(1, 5).subscribe();
    const req = http.expectOne((r) => r.url === '/api/claims/queue');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('5');
    req.flush({});
  });
});
