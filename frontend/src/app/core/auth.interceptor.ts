import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { PUBLIC_AUTH_PATHS } from './api.service';
import { AuthService } from './auth.service';

const isPublicAuthCall = (url: string): boolean => PUBLIC_AUTH_PATHS.includes(url);

/**
 * Adds the bearer token to API calls (never to login/register, never to other origins), and turns a 401 on any other call
 * into "your session ended": sign out and go to the login page. A 401 from login/register is just wrong credentials.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  if (token && req.url.startsWith('/api/') && !isPublicAuthCall(req.url)) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && !isPublicAuthCall(req.url) && auth.isLoggedIn()) {
        auth.logout(true);
      }
      return throwError(() => err);
    }),
  );
};
