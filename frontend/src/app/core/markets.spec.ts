import { MARKETS, marketName } from './markets';

describe('markets', () => {
  it('offers six markets, each with a unique two-letter upper-case code', () => {
    expect(MARKETS).toHaveLength(6);
    expect(new Set(MARKETS.map((m) => m.code)).size).toBe(6);
    for (const m of MARKETS) {
      expect(m.code).toMatch(/^[A-Z]{2}$/); // what the backend's market rule accepts
      expect(m.name.length).toBeGreaterThan(1);
    }
  });

  it('names a market from its code, whatever the case', () => {
    expect(marketName('SG')).toBe('Singapore');
    expect(marketName('hk')).toBe('Hong Kong');
  });

  it('falls back to the code for a market that is not in the list, and tolerates a missing one', () => {
    expect(marketName('AU')).toBe('AU');
    expect(marketName(null)).toBe('');
    expect(marketName(undefined)).toBe('');
  });
});
