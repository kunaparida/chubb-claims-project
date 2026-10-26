import { elapsedLabel, incidentWindow, isoDate, shiftIsoDate } from './dates';

describe('dates', () => {
  describe('isoDate / shiftIsoDate', () => {
    it('formats a calendar date with zero padding', () => {
      expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
      expect(isoDate(new Date(2026, 10, 30))).toBe('2026-11-30');
    });

    it('moves across month and year boundaries', () => {
      expect(shiftIsoDate('2026-03-03', -7)).toBe('2026-02-24');
      expect(shiftIsoDate('2026-01-02', -7)).toBe('2025-12-26');
      expect(shiftIsoDate('2026-12-30', 3)).toBe('2027-01-02');
    });
  });

  describe('incidentWindow', () => {
    it('runs from seven days ago to today, both included', () => {
      const noon = new Date('2026-10-26T12:00:00Z'); // the same calendar day in every time zone from UTC-12 to UTC+11
      expect(incidentWindow(noon)).toEqual({ min: '2026-10-19', max: '2026-10-26' });
    });

    it('never offers a date later than the UTC date, which is the clock a containerised backend uses', () => {
      for (const iso of ['2026-10-26T00:30:00Z', '2026-10-26T12:00:00Z', '2026-10-26T23:30:00Z']) {
        const now = new Date(iso);
        expect(incidentWindow(now).max <= now.toISOString().slice(0, 10)).toBe(true);
        expect(incidentWindow(now).max <= isoDate(now)).toBe(true);
      }
    });

    it('is always exactly seven days wide', () => {
      const { min, max } = incidentWindow(new Date());
      expect(shiftIsoDate(max, -7)).toBe(min);
    });
  });

  describe('elapsedLabel', () => {
    const now = new Date('2026-10-26T12:00:00Z').getTime();
    const ago = (ms: number) => new Date(now - ms).toISOString();

    it.each([
      [10_000, 'just now'],
      [5 * 60_000, '5 min'],
      [59 * 60_000, '59 min'],
      [3 * 3_600_000, '3 h'],
      [23 * 3_600_000, '23 h'],
      [24 * 3_600_000, '1 day'],
      [50 * 3_600_000, '2 days'],
    ])('%d ms ago is %s', (ms, label) => {
      expect(elapsedLabel(ago(ms), now)).toBe(label);
    });

    it('treats a time in the future as just now', () => {
      expect(elapsedLabel(new Date(now + 60_000).toISOString(), now)).toBe('just now');
    });
  });
});
