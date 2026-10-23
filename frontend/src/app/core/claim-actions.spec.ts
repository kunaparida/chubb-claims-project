import { claim } from '../testing';
import { ClaimActionId, NOTE_ACTIONS, Viewer, allowedActions, isTerminal } from './claim-actions';
import { CLAIM_STATUSES, ClaimStatus } from './models';

/**
 * An independent copy of the backend's rules (`ClaimService.TRANSITIONS`), used as the oracle. If the UI's action table ever
 * offers something the backend's workflow forbids — or hides something it allows — these tests fail.
 */
const BACKEND_TRANSITIONS: Record<ClaimStatus, ClaimStatus[]> = {
  SUBMITTED: ['UNDER_REVIEW'],
  UNDER_REVIEW: ['MORE_INFO_REQUIRED', 'APPROVED', 'REJECTED'],
  MORE_INFO_REQUIRED: ['UNDER_REVIEW'],
  APPROVED: ['SETTLED'],
  REJECTED: [],
  SETTLED: [],
};

/** Status each action moves the claim to (assign/pickup change the officer only). */
const TARGET: Partial<Record<ClaimActionId, ClaimStatus>> = {
  REVIEW: 'UNDER_REVIEW',
  REQUEST_INFO: 'MORE_INFO_REQUIRED',
  PROVIDE_INFO: 'UNDER_REVIEW',
  APPROVE: 'APPROVED',
  REJECT: 'REJECTED',
  SETTLE: 'SETTLED',
};

const CLAIMANT_OWNER: Viewer = { id: 1, role: 'CLAIMANT' };
const CLAIMANT_OTHER: Viewer = { id: 2, role: 'CLAIMANT' };
const OFFICER_ME: Viewer = { id: 7, role: 'OFFICER' };
const MANAGER: Viewer = { id: 5, role: 'MANAGER' };

const ASSIGNED_TO_ME = 7;
const ASSIGNED_TO_OTHER = 9;

const withStatus = (status: ClaimStatus, assignedOfficerId: number | null) =>
  claim({ status, assignedOfficerId, assignedOfficerName: assignedOfficerId ? 'Someone' : null });

describe('allowedActions', () => {
  describe('every offered transition is legal in the backend workflow', () => {
    for (const status of CLAIM_STATUSES) {
      for (const assigned of [null, ASSIGNED_TO_ME, ASSIGNED_TO_OTHER]) {
        for (const viewer of [CLAIMANT_OWNER, CLAIMANT_OTHER, OFFICER_ME, MANAGER]) {
          it(`${viewer.role}#${viewer.id} on ${status}, officer=${assigned}`, () => {
            for (const action of allowedActions(withStatus(status, assigned), viewer)) {
              const target = TARGET[action];
              if (target) {
                expect(BACKEND_TRANSITIONS[status], `${action} must be legal from ${status}`).toContain(target);
              }
            }
          });
        }
      }
    }
  });

  describe('nothing legal is hidden from someone who may act', () => {
    for (const status of CLAIM_STATUSES) {
      const legalStaffActions = (Object.keys(TARGET) as ClaimActionId[]).filter(
        (a) => a !== 'PROVIDE_INFO' && BACKEND_TRANSITIONS[status].includes(TARGET[a] as ClaimStatus),
      );

      it(`the assigned officer gets exactly the legal staff actions on ${status}`, () => {
        const offered = allowedActions(withStatus(status, ASSIGNED_TO_ME), OFFICER_ME);
        expect([...offered].sort()).toEqual([...legalStaffActions].sort());
      });

      it(`a manager gets the legal staff actions on ${status} without being assigned${isTerminal(status) ? '' : ', plus ASSIGN'}`, () => {
        const offered = allowedActions(withStatus(status, null), MANAGER);
        expect(offered.filter((a) => a !== 'ASSIGN').sort()).toEqual([...legalStaffActions].sort());
        expect(offered.includes('ASSIGN')).toBe(!isTerminal(status));
      });
    }
  });

  describe('who may do what', () => {
    it('only the owning claimant can answer, and only while information is required', () => {
      expect(allowedActions(withStatus('MORE_INFO_REQUIRED', ASSIGNED_TO_ME), CLAIMANT_OWNER)).toEqual(['PROVIDE_INFO']);
      expect(allowedActions(withStatus('MORE_INFO_REQUIRED', ASSIGNED_TO_ME), CLAIMANT_OTHER)).toEqual([]);
      for (const status of CLAIM_STATUSES.filter((s) => s !== 'MORE_INFO_REQUIRED')) {
        expect(allowedActions(withStatus(status, ASSIGNED_TO_ME), CLAIMANT_OWNER), status).toEqual([]);
      }
    });

    it('an officer picks up only unassigned SUBMITTED claims from the queue', () => {
      expect(allowedActions(withStatus('SUBMITTED', null), OFFICER_ME)).toEqual(['PICKUP']);
    });

    it('an officer is offered nothing on a claim assigned to someone else', () => {
      for (const status of CLAIM_STATUSES) {
        expect(allowedActions(withStatus(status, ASSIGNED_TO_OTHER), OFFICER_ME), status).toEqual([]);
      }
    });

    it('picking up is not reviewing: an assigned SUBMITTED claim offers REVIEW, not PICKUP', () => {
      expect(allowedActions(withStatus('SUBMITTED', ASSIGNED_TO_ME), OFFICER_ME)).toEqual(['REVIEW']);
    });

    it('a manager can assign any open claim and never a closed one', () => {
      for (const status of CLAIM_STATUSES) {
        expect(allowedActions(withStatus(status, ASSIGNED_TO_OTHER), MANAGER).includes('ASSIGN'), status).toBe(
          !isTerminal(status),
        );
      }
    });

    it('closed claims offer nothing to claimants or officers', () => {
      for (const status of ['REJECTED', 'SETTLED'] as const) {
        for (const viewer of [CLAIMANT_OWNER, OFFICER_ME]) {
          expect(allowedActions(withStatus(status, ASSIGNED_TO_ME), viewer)).toEqual([]);
        }
      }
    });
  });

  it('the note-carrying actions are exactly those the backend gives a NoteRequest', () => {
    expect([...NOTE_ACTIONS].sort()).toEqual(['APPROVE', 'REJECT', 'REQUEST_INFO', 'SETTLE']);
  });
});
