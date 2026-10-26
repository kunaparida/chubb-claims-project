import { ClaimStatus } from './models';

export type StepState = 'done' | 'current' | 'todo' | 'warn' | 'danger';

export interface ProgressStep {
  label: string;
  state: StepState;
}

/**
 * The four stages a claimant sees: Submitted, Under review, Decision, Settled. Purely presentational: it shows where a claim is,
 * it decides nothing (what a person may DO comes from `claim-actions.ts`).
 */
export function claimProgress(status: ClaimStatus): ProgressStep[] {
  const step = (label: string, state: StepState): ProgressStep => ({ label, state });
  switch (status) {
    case 'SUBMITTED':
      return [step('Submitted', 'current'), step('Under review', 'todo'), step('Decision', 'todo'), step('Settled', 'todo')];
    case 'UNDER_REVIEW':
      return [step('Submitted', 'done'), step('Under review', 'current'), step('Decision', 'todo'), step('Settled', 'todo')];
    case 'MORE_INFO_REQUIRED':
      return [step('Submitted', 'done'), step('More information', 'warn'), step('Decision', 'todo'), step('Settled', 'todo')];
    case 'APPROVED':
      return [step('Submitted', 'done'), step('Under review', 'done'), step('Approved', 'done'), step('Settled', 'current')];
    case 'REJECTED':
      return [step('Submitted', 'done'), step('Under review', 'done'), step('Rejected', 'danger')];
    case 'SETTLED':
      return [step('Submitted', 'done'), step('Under review', 'done'), step('Approved', 'done'), step('Settled', 'done')];
  }
}
