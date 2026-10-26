/** Six random digits, 100000 to 999999, from the browser's secure random source. */
function sixDigits(): string {
  const buffer = new Uint32Array(1);
  globalThis.crypto.getRandomValues(buffer);
  return String(100000 + (buffer[0] % 900000));
}

/**
 * A new policy reference such as `POL-2026-483920` (the year, then six digits). The claimant does not type one: the form fills it in.
 * The backend only requires a non-blank value of at most 64 characters, so this is a UI convention, not part of the API contract.
 */
export function generatePolicyNumber(now: Date = new Date(), digits: () => string = sixDigits): string {
  return `POL-${now.getFullYear()}-${digits()}`;
}
