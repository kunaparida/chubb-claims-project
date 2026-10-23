import { HttpErrorResponse } from '@angular/common/http';
import { fieldErrors, problemMessage } from './problem';

const http = (status: number, error: unknown) => new HttpErrorResponse({ status, error, statusText: 'x' });

describe('problem helpers', () => {
  it('shows the backend `detail` for business-rule, conflict and auth errors', () => {
    expect(problemMessage(http(409, { detail: 'Invalid claim status transition: SUBMITTED -> APPROVED' }))).toBe(
      'Invalid claim status transition: SUBMITTED -> APPROVED',
    );
    expect(problemMessage(http(403, { detail: 'Only the assigned officer or a manager can perform this action' }))).toBe(
      'Only the assigned officer or a manager can perform this action',
    );
    expect(problemMessage(http(401, { detail: 'Invalid email or password' }))).toBe('Invalid email or password');
  });

  it('joins the per-field messages for a 400 validation failure', () => {
    const err = http(400, {
      detail: 'Request validation failed',
      errors: { policyNumber: 'must not be blank', market: 'must be a 2-letter market code such as SG' },
    });
    expect(problemMessage(err)).toBe('must not be blank · must be a 2-letter market code such as SG');
    expect(fieldErrors(err)).toEqual({
      policyNumber: 'must not be blank',
      market: 'must be a 2-letter market code such as SG',
    });
  });

  it('uses `detail` for a 400 that has no field errors', () => {
    expect(problemMessage(http(400, { detail: 'Request body is missing or malformed' }))).toBe(
      'Request body is missing or malformed',
    );
  });

  it('explains an unreachable backend plainly', () => {
    expect(problemMessage(http(0, null))).toContain('Cannot reach the server');
  });

  it.each([502, 503, 504])(
    'a %i with no problem body (what the dev proxy returns when the backend is down) also means unreachable',
    (status) => {
      expect(problemMessage(http(status, null))).toContain('Cannot reach the server');
      expect(problemMessage(http(status, ''))).toContain('Cannot reach the server');
    },
  );

  it('but a 5xx that carries a backend message shows that message, and a plain 500 stays generic', () => {
    expect(problemMessage(http(503, { detail: 'Database is restarting' }))).toBe('Database is restarting');
    expect(problemMessage(http(500, null))).toBe('Something went wrong. Please try again.');
  });

  it('falls back to a friendly message for 403/404 without a body and never leaks unknown shapes', () => {
    expect(problemMessage(http(403, null))).toBe('You do not have permission to do that.');
    expect(problemMessage(http(404, null))).toBe('That could not be found.');
    expect(problemMessage(http(500, '<html>stack trace</html>'))).toBe('Something went wrong. Please try again.');
    expect(problemMessage(new Error('boom'), 'custom fallback')).toBe('custom fallback');
  });

  it('fieldErrors is empty for anything that is not a 400', () => {
    expect(fieldErrors(http(409, { errors: { a: 'b' } }))).toEqual({});
    expect(fieldErrors('nope')).toEqual({});
  });
});
