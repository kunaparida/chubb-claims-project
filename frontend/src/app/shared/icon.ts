import { Component, computed, input } from '@angular/core';

/** Outline icons on a 24x24 grid, each a single path. Drawn for this app; no icon library is needed. */
const PATHS = {
  file: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h6',
  plus: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 8v8M8 12h8',
  inbox: 'M3 13l2.2-7.5A2 2 0 0 1 7.1 4h9.8a2 2 0 0 1 1.9 1.5L21 13v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM3 13h5l1.2 2.5h5.6L16 13h5',
  grid: 'M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z',
  list: 'M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20a8 8 0 0 1 16 0',
  logout: 'M10 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h4M15 16l4-4-4-4M19 12H9',
  refresh: 'M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5',
  check: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 12.5l2.8 2.8L16 9.5',
  alert: 'M12 3.5l9.5 16.5h-19zM12 10v4.5M12 17.2h.01',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3.2 2',
  calendar: 'M4 6h16v14H4zM4 10.5h16M8.5 3.5v4M15.5 3.5v4',
  back: 'M19 12H5M11 18l-6-6 6-6',
  shield: 'M12 3l7.5 2.8v5.7c0 4.6-3.1 7.9-7.5 9.5-4.4-1.6-7.5-4.9-7.5-9.5V5.8zM8.8 12.2l2.2 2.2 4.2-4.4',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  car: 'M6.5 10l1.7-4h7.6l1.7 4M3 12a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5H3zM9.5 17.5a2 2 0 1 1-4 0 2 2 0 0 1 4 0zM18.5 17.5a2 2 0 1 1-4 0 2 2 0 0 1 4 0z',
  home: 'M4 11l8-7 8 7M6 9.5V20h12V9.5M10 20v-5h4v5',
  hash: 'M5 9h14M5 15h14M10 4L8 20M16 4l-2 16',
  eye: 'M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  'eye-off': 'M3 3l18 18M10.6 6a8.4 8.4 0 0 1 1.4-.1c6 0 9.5 6.1 9.5 6.1a16 16 0 0 1-3.1 3.8M6.5 7.6A15.8 15.8 0 0 0 2.5 12S6 18.1 12 18.1a8.6 8.6 0 0 0 3.2-.6M9.9 10a3 3 0 0 0 4.1 4.2',
  lock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3',
  upload: 'M12 16V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  download: 'M12 4v12M7 11l5 5 5-5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  paperclip: 'M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7L9.5 17.2a1.7 1.7 0 0 1-2.4-2.4l7.4-7.4',
  image: 'M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M9 9.5h.01',
  close: 'M6 6l12 12M18 6L6 18',
  refresh2: 'M4 12a8 8 0 0 1 13.7-5.6L20 8.5M20 4v4.5h-4.5M20 12a8 8 0 0 1-13.7 5.6L4 15.5M4 20v-4.5h4.5',
} as const;

export type IconName = keyof typeof PATHS;

/** `<app-icon name="file" />`: decorative (aria-hidden); put the meaning in the surrounding text or an aria-label. */
@Component({
  selector: 'app-icon',
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path [attr.d]="path()" /></svg>`,
  host: { class: 'icon' },
})
export class Icon {
  readonly name = input.required<IconName>();
  protected readonly path = computed(() => PATHS[this.name()]);
}
