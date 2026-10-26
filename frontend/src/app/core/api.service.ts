import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthResponse,
  Claim,
  ClaimDocument,
  ClaimHistoryEntry,
  ClaimStatus,
  CreateClaimRequest,
  Liability,
  LoginRequest,
  OfficerPerformance,
  PageResponse,
  RegisterRequest,
  UserProfile,
  Workload,
} from './models';

/**
 * Calls that must NOT carry a bearer token and whose 401 means "wrong credentials", not "session expired".
 * The auth interceptor reads this so URLs are defined in exactly one file.
 */
export const PUBLIC_AUTH_PATHS: readonly string[] = ['/api/auth/login', '/api/auth/register'];

/**
 * The only place that knows URLs and HTTP verbs. Components call methods; they never build requests.
 * All paths are relative: in development the dev server proxies `/api` to the backend (the backend has no CORS configuration).
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- auth ----
  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', body);
  }

  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', body);
  }

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>('/api/auth/me');
  }

  // ---- claims: read ----
  /** Role-scoped by the server: claimant = own, officer = assigned to them, manager = all. Newest first. */
  listClaims(opts: { page?: number; size?: number; status?: ClaimStatus | '' } = {}): Observable<PageResponse<Claim>> {
    let params = new HttpParams();
    if (opts.page !== undefined) params = params.set('page', opts.page);
    if (opts.size !== undefined) params = params.set('size', opts.size);
    if (opts.status) params = params.set('status', opts.status);
    return this.http.get<PageResponse<Claim>>('/api/claims', { params });
  }

  /** Unassigned SUBMITTED claims, oldest first. Officers and managers only. */
  queue(page = 0, size = 20): Observable<PageResponse<Claim>> {
    return this.http.get<PageResponse<Claim>>('/api/claims/queue', { params: { page, size } });
  }

  getClaim(id: number): Observable<Claim> {
    return this.http.get<Claim>(`/api/claims/${id}`);
  }

  history(id: number): Observable<ClaimHistoryEntry[]> {
    return this.http.get<ClaimHistoryEntry[]>(`/api/claims/${id}/history`);
  }

  // ---- claims: commands ----
  createClaim(body: CreateClaimRequest): Observable<Claim> {
    return this.http.post<Claim>('/api/claims', body);
  }

  pickup(id: number): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/pickup`, null);
  }

  assign(id: number, officerId: number): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/assign`, { officerId });
  }

  review(id: number): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/review`, null);
  }

  requestInfo(id: number, note: string): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/request-info`, { note });
  }

  provideInfo(id: number, response: string): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/provide-info`, { response });
  }

  approve(id: number, note: string): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/approve`, { note });
  }

  reject(id: number, note: string): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/reject`, { note });
  }

  settle(id: number, note: string): Observable<Claim> {
    return this.http.put<Claim>(`/api/claims/${id}/settle`, { note });
  }

  // ---- claim documents ----
  /** Details of the files attached to a claim (not their bytes). Anyone who can see the claim can list them. */
  documents(claimId: number): Observable<ClaimDocument[]> {
    return this.http.get<ClaimDocument[]>(`/api/claims/${claimId}/documents`);
  }

  /** Attaches one file as multipart/form-data in the `file` field. Claimant only, while the claim is open. */
  uploadDocument(claimId: number, file: File): Observable<ClaimDocument> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.post<ClaimDocument>(`/api/claims/${claimId}/documents`, body);
  }

  /** The file itself. The bearer token travels in the header, so a plain link would not work: the bytes are fetched, then saved. */
  downloadDocument(claimId: number, documentId: number): Observable<Blob> {
    return this.http.get(`/api/claims/${claimId}/documents/${documentId}`, { responseType: 'blob' });
  }

  // ---- manager dashboard ----
  workload(): Observable<Workload> {
    return this.http.get<Workload>('/api/dashboard/workload');
  }

  liability(): Observable<Liability> {
    return this.http.get<Liability>('/api/dashboard/liability');
  }

  /** Lists ALL officers (also the source for the manager's assign picker — there is no officers endpoint). */
  performance(): Observable<OfficerPerformance[]> {
    return this.http.get<OfficerPerformance[]>('/api/dashboard/performance');
  }
}
