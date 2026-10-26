/** How far back an incident may be reported. */
export const INCIDENT_WINDOW_DAYS = 7;

/** A date as YYYY-MM-DD using the viewer's calendar (not UTC). */
export function isoDate(d: Date): string {
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

/** The date `days` before (negative) or after (positive) an ISO date, as an ISO date. */
export function shiftIsoDate(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return isoDate(new Date(y, m - 1, d + days));
}

/**
 * The dates an incident can be reported for: from seven days ago up to today, both included.
 *
 * "Today" is the EARLIER of the viewer's date and the UTC date. The backend refuses a future incident date by its own clock, and a
 * server in a container usually runs on UTC, so a claimant in Asia early in the morning would otherwise be offered a date the
 * server still counts as tomorrow. The cost is that those first hours of the day cannot pick "today".
 */
export function incidentWindow(now: Date = new Date()): { min: string; max: string } {
  const local = isoDate(now);
  const utc = now.toISOString().slice(0, 10);
  const max = local < utc ? local : utc;
  return { min: shiftIsoDate(max, -INCIDENT_WINDOW_DAYS), max };
}

/** How long ago an instant was, in the largest sensible unit: "just now", "12 min", "5 h", "3 days". */
export function elapsedLabel(iso: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60_000));
  if (minutes < 1) {
    return 'just now';
  }
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} h`;
  }
  const days = Math.floor(hours / 24);
  return days === 1 ? '1 day' : `${days} days`;
}
