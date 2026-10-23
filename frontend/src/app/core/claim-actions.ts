import { Claim, ClaimStatus, Role } from './models';

/**
 * THE ONLY PLACE the UI decides who can do what to a claim. It mirrors the backend's `ClaimService` (see CLAUDE.md). The backend
 * does not return "allowed actions", so this is a convenience for showing sensible buttons — the server stays the authority and
 * every action must still handle 403/404/409 gracefully.
 */
export type ClaimActionId =
  | 'PICKUP'
  | 'ASSIGN'
  | 'REVIEW'
  | 'REQUEST_INFO'
  | 'PROVIDE_INFO'
  | 'APPROVE'
  | 'REJECT'
  | 'SETTLE';

export interface Viewer {
  id: number;
  role: Role;
}

export const STATUS_LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  MORE_INFO_REQUIRED: 'More information required',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SETTLED: 'Settled',
};

export const ACTION_LABELS: Record<ClaimActionId, string> = {
  PICKUP: 'Pick up this claim',
  ASSIGN: 'Assign to an officer',
  REVIEW: 'Start review',
  REQUEST_INFO: 'Request information',
  PROVIDE_INFO: 'Send information',
  APPROVE: 'Approve',
  REJECT: 'Reject',
  SETTLE: 'Mark as settled',
};

/** Actions that carry a mandatory free-text note on the backend (`NoteRequest`). */
export const NOTE_ACTIONS: readonly ClaimActionId[] = ['REQUEST_INFO', 'APPROVE', 'REJECT', 'SETTLE'];

const TERMINAL: readonly ClaimStatus[] = ['REJECTED', 'SETTLED'];

/** What an officer who owns the claim — or any manager — may do next, by status (backend `TRANSITIONS`). */
const STAFF_ACTIONS: Record<ClaimStatus, readonly ClaimActionId[]> = {
  SUBMITTED: ['REVIEW'],
  UNDER_REVIEW: ['REQUEST_INFO', 'APPROVE', 'REJECT'],
  MORE_INFO_REQUIRED: ['REVIEW'],
  APPROVED: ['SETTLE'],
  REJECTED: [],
  SETTLED: [],
};

export function allowedActions(claim: Claim, viewer: Viewer): ClaimActionId[] {
  switch (viewer.role) {
    case 'CLAIMANT':
      return claim.claimantId === viewer.id && claim.status === 'MORE_INFO_REQUIRED' ? ['PROVIDE_INFO'] : [];

    case 'OFFICER':
      if (claim.assignedOfficerId === null) {
        return claim.status === 'SUBMITTED' ? ['PICKUP'] : []; // the queue: anyone may pick it up
      }
      return claim.assignedOfficerId === viewer.id ? [...STAFF_ACTIONS[claim.status]] : [];

    case 'MANAGER': {
      // managers need no assignment to act, and are the only ones who can assign (the backend allows re-assigning
      // closed claims, but nobody should be offered that)
      const actions = [...STAFF_ACTIONS[claim.status]];
      return TERMINAL.includes(claim.status) ? actions : ['ASSIGN', ...actions];
    }
  }
}

export function isTerminal(status: ClaimStatus): boolean {
  return TERMINAL.includes(status);
}
