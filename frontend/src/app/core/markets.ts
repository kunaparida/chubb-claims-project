/**
 * The markets a claim can be reported in. The backend only requires a two-letter code and does not list the markets, so this
 * list (six APAC markets) lives in the UI: it is what the "Market" dropdown offers. Claims already stored under another code are
 * still shown correctly, just by their code.
 */
export interface Market {
  /** Two-letter code sent to the backend. */
  code: string;
  name: string;
}

export const MARKETS: readonly Market[] = [
  { code: 'SG', name: 'Singapore' },
  { code: 'HK', name: 'Hong Kong' },
  { code: 'IN', name: 'India' },
  { code: 'MY', name: 'Malaysia' },
  { code: 'TH', name: 'Thailand' },
  { code: 'ID', name: 'Indonesia' },
];

/** "Singapore" for "SG"; an unknown code is returned as it is. */
export function marketName(code: string | null | undefined): string {
  const upper = (code ?? '').toUpperCase();
  return MARKETS.find((m) => m.code === upper)?.name ?? (code ?? '');
}
