import { AuthResponse, Claim, ClaimDocument, ClaimHistoryEntry, ClaimStatus, PageResponse, Role } from './core/models';

/** Shared test fixtures (not part of the app bundle: only spec files import this). */

export function authResponse(role: Role = 'OFFICER', over: Partial<AuthResponse> = {}): AuthResponse {
  return {
    token: `token-for-${role}`,
    tokenType: 'Bearer',
    expiresIn: 3_600_000,
    userId: role === 'CLAIMANT' ? 1 : role === 'OFFICER' ? 7 : 5,
    email: `${role.toLowerCase()}@example.com`,
    fullName: role === 'CLAIMANT' ? 'Chen Wei' : role === 'OFFICER' ? 'Priya Nair' : 'Mei Tan',
    role,
    ...over,
  };
}

export function claim(over: Partial<Claim> = {}): Claim {
  return {
    id: 42,
    claimNumber: 'CLM-2026-0042',
    policyNumber: 'POL-2026-00042',
    claimantName: 'Chen Wei',
    claimantEmail: 'claimant@example.com',
    incidentDate: '2026-10-10',
    description: 'Rear bumper damage from a parking collision.',
    amountClaimed: 1250.5,
    claimType: 'MOTOR',
    market: 'SG',
    status: 'SUBMITTED',
    claimantId: 1,
    assignedOfficerId: null,
    assignedOfficerName: null,
    informationRequest: null,
    informationResponse: null,
    decisionNotes: null,
    createdAt: '2026-10-11T04:00:00Z',
    updatedAt: '2026-10-11T04:00:00Z',
    ...over,
  };
}

export function claimDocument(over: Partial<ClaimDocument> = {}): ClaimDocument {
  return {
    id: 1,
    fileName: 'repair-estimate.pdf',
    contentType: 'application/pdf',
    sizeBytes: 184_320,
    uploadedAt: '2026-10-11T05:00:00Z',
    uploadedByName: 'Chen Wei',
    ...over,
  };
}

export function page<T>(content: T[], over: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
    ...over,
  };
}

export function historyEntry(toStatus: ClaimStatus, over: Partial<ClaimHistoryEntry> = {}): ClaimHistoryEntry {
  return {
    id: 1,
    fromStatus: null,
    toStatus,
    changedById: 1,
    changedByName: 'Chen Wei',
    note: 'Claim submitted',
    changedAt: '2026-10-11T04:00:00Z',
    ...over,
  };
}

export const STORAGE_KEY = 'claims.ui.session';

/** Put a signed-in session in storage BEFORE the first AuthService is created (ids: claimant 1, officer 7, manager 5). */
export function storeSession(role: Role, expiresInMs = 3_600_000): void {
  const r = authResponse(role);
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      token: r.token,
      expiresAt: Date.now() + expiresInMs,
      user: { id: r.userId, email: r.email, fullName: r.fullName, role: r.role },
    }),
  );
}

/** Type into an input/textarea the way a user does (fires `input`). */
export function type(root: HTMLElement, selector: string, value: string): void {
  const el = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector);
  if (!el) {
    throw new Error(`no element for ${selector}`);
  }
  el.value = value;
  el.dispatchEvent(new Event('input'));
}

/** Pick an option in a <select> (fires `change`). */
export function choose(root: HTMLElement, selector: string, value: string): void {
  const el = root.querySelector<HTMLSelectElement>(selector);
  if (!el) {
    throw new Error(`no element for ${selector}`);
  }
  el.value = value;
  el.dispatchEvent(new Event('change'));
}

/** Visible labels of the buttons inside a container. */
export function buttonLabels(root: HTMLElement, selector = 'button'): string[] {
  return Array.from(root.querySelectorAll<HTMLButtonElement>(selector)).map((b) => (b.textContent ?? '').trim());
}
