import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Must be signed in. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/**
 * Must be signed in AND hold one of the roles; a wrong-role visit goes to that user's own home instead of an error page.
 * This only steers navigation — the backend is what actually enforces access, whatever the UI shows.
 */
export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.user();
    if (!user) {
      return router.createUrlTree(['/login']);
    }
    return roles.includes(user.role) ? true : router.createUrlTree([auth.homeFor(user.role)]);
  };

/** The bare "/" sends each role to its own landing page. */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const user = auth.user();
  return inject(Router).createUrlTree([user ? auth.homeFor(user.role) : '/login']);
};
