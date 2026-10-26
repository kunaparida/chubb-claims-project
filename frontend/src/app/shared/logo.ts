import { Component, input } from '@angular/core';

let nextLogoId = 0;

/**
 * The product mark: a shield with a tick on a blue gradient tile, next to the name. Drawn for this project (it is not any
 * company's logo). The same artwork is in `public/logo.svg`, which the favicon uses.
 *
 * Every instance gets its own gradient id: a gradient defined inside a hidden element (the brand panel on small screens) cannot
 * be used by a second logo that shares its id, which left the tile blank.
 */
@Component({
  selector: 'app-logo',
  template: `
    <span class="logo" [class.logo-light]="light()">
      <svg class="logo-mark" viewBox="0 0 48 48" role="img" aria-label="Claims Platform logo">
        <defs>
          <linearGradient [attr.id]="gradientId" x1="6" y1="4" x2="44" y2="46" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#4f8bff" />
            <stop offset="1" stop-color="#2b3fd6" />
          </linearGradient>
        </defs>
        <rect width="48" height="48" rx="13" [attr.fill]="'url(#' + gradientId + ')'" />
        <path d="M24 9.5l11 4v8.2c0 6.9-4.5 11.8-11 14.3-6.5-2.5-11-7.4-11-14.3v-8.2z" fill="rgba(255,255,255,0.18)" stroke="#fff" stroke-width="2.2" stroke-linejoin="round" />
        <path d="M18.5 23.5l4 4 7.5-8" fill="none" stroke="#fff" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
      @if (!compact()) {
        <span class="logo-text">
          <strong>Claims Platform</strong>
          <small>APAC · Motor &amp; Property</small>
        </span>
      }
    </span>
  `,
})
export class Logo {
  /** Light text for use on a dark background. */
  readonly light = input(false);
  /** Mark only, without the name. */
  readonly compact = input(false);

  protected readonly gradientId = `logo-tile-${nextLogoId++}`;
}
