import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';
import { Icon, IconName } from '../shared/icon';
import { Logo } from '../shared/logo';

interface NavItem {
  label: string;
  link: string;
  icon: IconName;
}

/** Navigation per role. The same shell serves everyone; only the links differ. */
const NAV: Record<Role, NavItem[]> = {
  CLAIMANT: [
    { label: 'My claims', link: '/claims', icon: 'file' },
    { label: 'Report an incident', link: '/claims/new', icon: 'plus' },
  ],
  OFFICER: [
    { label: 'Work queue', link: '/queue', icon: 'inbox' },
    { label: 'Assigned to me', link: '/claims', icon: 'user' },
  ],
  MANAGER: [
    { label: 'Dashboard', link: '/dashboard', icon: 'grid' },
    { label: 'Work queue', link: '/queue', icon: 'inbox' },
    { label: 'All claims', link: '/claims', icon: 'list' },
  ],
};

const ROLE_LABEL: Record<Role, string> = {
  CLAIMANT: 'Claimant',
  OFFICER: 'Claims officer',
  MANAGER: 'Manager',
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Logo],
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

  /** Up to two initials of the signed-in person's name, for the avatar. */
  protected readonly initials = computed(() => {
    const words = (this.auth.user()?.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    return words.slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '?';
  });

  constructor() {
    this.auth.refreshProfile();
  }
}
