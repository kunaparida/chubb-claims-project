import { Component } from '@angular/core';
import { Icon } from './icon';
import { Logo } from './logo';

/**
 * The frame around the sign-in and registration forms: a brand panel on the left (hidden on small screens) and the form, projected
 * from the page, on the right.
 */
@Component({
  selector: 'app-auth-shell',
  imports: [Logo, Icon],
  template: `
    <div class="auth-layout">
      <aside class="auth-brand">
        <app-logo [light]="true" />
        <div class="auth-pitch">
          <h2>Claims, handled with clarity.</h2>
          <p>
            Report an incident, follow it to a decision, and settle it: one platform for claimants, claims officers and managers.
          </p>
          <ul class="feature-list">
            <li><app-icon name="shield" /> Role-based access and a full audit trail</li>
            <li><app-icon name="clock" /> A live work queue and claim status tracking</li>
            <li><app-icon name="chart" /> Liability exposure by market for managers</li>
          </ul>
        </div>
        <p class="auth-foot">Assessment build · demo data only</p>
      </aside>
      <main class="auth-panel">
        <ng-content />
      </main>
    </div>
  `,
})
export class AuthShell {}
