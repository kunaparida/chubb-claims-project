import { claimProgress } from './claim-progress';
import { CLAIM_STATUSES } from './models';

describe('claimProgress', () => {
  const states = (status: Parameters<typeof claimProgress>[0]) => claimProgress(status).map((s) => `${s.label}:${s.state}`);

  it('shows a new claim as submitted, with everything else ahead', () => {
    expect(states('SUBMITTED')).toEqual(['Submitted:current', 'Under review:todo', 'Decision:todo', 'Settled:todo']);
  });

  it('shows review under way', () => {
    expect(states('UNDER_REVIEW')).toEqual(['Submitted:done', 'Under review:current', 'Decision:todo', 'Settled:todo']);
  });

  it('flags a claim that is waiting for the claimant', () => {
    expect(states('MORE_INFO_REQUIRED')).toEqual(['Submitted:done', 'More information:warn', 'Decision:todo', 'Settled:todo']);
  });

  it('shows an approved claim as decided and waiting to be settled', () => {
    expect(states('APPROVED')).toEqual(['Submitted:done', 'Under review:done', 'Approved:done', 'Settled:current']);
  });

  it('ends a rejected claim at the rejection, with no settlement step', () => {
    expect(states('REJECTED')).toEqual(['Submitted:done', 'Under review:done', 'Rejected:danger']);
  });

  it('completes a settled claim', () => {
    expect(states('SETTLED')).toEqual(['Submitted:done', 'Under review:done', 'Approved:done', 'Settled:done']);
  });

  it('has an answer for every status, and never more than one current or flagged step', () => {
    for (const status of CLAIM_STATUSES) {
      const steps = claimProgress(status);
      expect(steps.length, status).toBeGreaterThanOrEqual(3);
      expect(steps.filter((s) => s.state === 'current' || s.state === 'warn' || s.state === 'danger').length, status).toBeLessThanOrEqual(1);
    }
  });
});
