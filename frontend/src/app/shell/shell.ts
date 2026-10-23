import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';

interface NavItem {
  label: string;
  link: string;
}

/** Navigation per role. The same shell serves everyone; only the links differ. */
const NAV: Record<Role, NavItem[]> = {
  CLAIMANT: [
    { label: 'My claims', link: '/claims' },
    { label: 'Report an incident', link: '/claims/new' },
  ],
  OFFICER: [
    { label: 'Work queue', link: '/queue' },
    { label: 'Assigned to me', link: '/claims' },
  ],
  MANAGER: [
    { label: 'Dashboard', link: '/dashboard' },
    { label: 'Work queue', link: '/queue' },
    { label: 'All claims', link: '/claims' },
  ],
};

const ROLE_LABEL: Record<Role, string> = {
  CLAIMANT: 'Claimant',
  OFFICER: 'Claims officer',
  MANAGER: 'Manager',
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
})
export class Shell {
  protected readonly auth = inject(AuthService);

  protected readonly nav = computed<NavItem[]>(() => {
    const role = this.auth.user()?.role;
    return role ? NAV[role] : [];
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.user()?.role;
    return role ? ROLE_LABEL[role] : '';
  });

  constructor() {
    this.auth.refreshProfile();
  }
}
