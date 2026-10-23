import { Routes } from '@angular/router';
import { authGuard, homeRedirectGuard, roleGuard } from './core/guards';
import { Shell } from './shell/shell';

/**
 * One app, three audiences: a shared shell, and role-specific lazy-loaded pages behind role guards.
 * Guards only steer navigation; the backend is what enforces access.
 */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  { path: 'register', loadComponent: () => import('./features/register/register').then((m) => m.Register) },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', canActivate: [homeRedirectGuard], children: [] },
      {
        // claimant: my claims · officer: claims assigned to me · manager: all claims — the server scopes the list by role
        path: 'claims',
        loadComponent: () => import('./features/claims/claims-list').then((m) => m.ClaimsList),
      },
      {
        path: 'claims/new',
        canActivate: [roleGuard('CLAIMANT')],
        loadComponent: () => import('./features/claims/new-claim').then((m) => m.NewClaim),
      },
      {
        path: 'claims/:id',
        loadComponent: () => import('./features/claims/claim-detail').then((m) => m.ClaimDetail),
      },
      {
        path: 'queue',
        canActivate: [roleGuard('OFFICER', 'MANAGER')],
        loadComponent: () => import('./features/queue/queue').then((m) => m.Queue),
      },
      {
        path: 'dashboard',
        canActivate: [roleGuard('MANAGER')],
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
