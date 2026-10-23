/**
 * Types for the backend REST contract (`chubb-claims-backendService`). They mirror its DTO records exactly — see CLAUDE.md.
 * The backend owns the contract: when it changes, change these types, then let the compiler show what breaks.
 */

export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';

export type ClaimStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'MORE_INFO_REQUIRED'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED';

export type ClaimType = 'MOTOR' | 'PROPERTY';

export const CLAIM_STATUSES: readonly ClaimStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'MORE_INFO_REQUIRED',
  'APPROVED',
  'REJECTED',
  'SETTLED',
];

// ---- auth ----

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  /** Token lifetime in milliseconds. */
  expiresIn: number;
  userId: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

// ---- claims ----

export interface Claim {
  id: number;
  claimNumber: string;
  policyNumber: string;
  claimantName: string;
  claimantEmail: string;
  /** ISO date, YYYY-MM-DD. */
  incidentDate: string;
  description: string;
  /** A bare number: the backend has no currency field. `market` says where it was incurred. */
  amountClaimed: number;
  claimType: ClaimType;
  /** 2-letter market code, upper case (e.g. SG). */
  market: string;
  status: ClaimStatus;
  claimantId: number;
  assignedOfficerId: number | null;
  assignedOfficerName: string | null;
  /** The officer's question, while information is required. */
  informationRequest: string | null;
  informationResponse: string | null;
  decisionNotes: string | null;
  /** ISO instants. */
  createdAt: string;
  updatedAt: string;
}

export interface CreateClaimRequest {
  policyNumber: string;
  claimantName: string;
  /** Must equal the signed-in account's email. */
  claimantEmail: string;
  incidentDate: string;
  description: string;
  amountClaimed: number;
  claimType: ClaimType;
  market: string;
}

export interface ClaimHistoryEntry {
  id: number;
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  changedById: number;
  changedByName: string;
  note: string | null;
  changedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  /** 0-based. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- manager dashboard ----

export interface Workload {
  unassignedClaims: number;
  totalOpenClaims: number;
  submitted: number;
  underReview: number;
  awaitingInformation: number;
  approved: number;
  rejected: number;
  settled: number;
}

export interface ExposureLine {
  /** Claim type (MOTOR/PROPERTY) or market code, depending on which list it is in. */
  key: string;
  pendingExposure: number;
  approvedAwaitingSettlement: number;
}

export interface Liability {
  /** Sums amounts across ALL markets (no currency conversion) — present it as unconverted. */
  pendingExposure: number;
  approvedAwaitingSettlement: number;
  byClaimType: ExposureLine[];
  byMarket: ExposureLine[];
}

export interface OfficerPerformance {
  officerId: number;
  officerName: string;
  assignedClaims: number;
  activeClaims: number;
  resolvedClaims: number;
}

// ---- errors ----

/** RFC 7807 body returned by every backend error. `errors` is present on 400 validation failures. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
}
