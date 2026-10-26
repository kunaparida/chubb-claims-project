import { generatePolicyNumber } from './policy-number';

describe('generatePolicyNumber', () => {
  it('is POL, the year and six digits', () => {
    expect(generatePolicyNumber(new Date(2026, 5, 1), () => '483920')).toBe('POL-2026-483920');
  });

  it('always fits the backend rule (not blank, at most 64 characters) and keeps six digits', () => {
    for (let i = 0; i < 200; i++) {
      const number = generatePolicyNumber();
      expect(number).toMatch(/^POL-\d{4}-\d{6}$/);
      expect(number.length).toBeLessThanOrEqual(64);
    }
  });

  it('is not the same number every time', () => {
    const numbers = new Set(Array.from({ length: 50 }, () => generatePolicyNumber()));
    expect(numbers.size).toBeGreaterThan(40);
  });
});
