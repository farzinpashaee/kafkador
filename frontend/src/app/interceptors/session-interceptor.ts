import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpInterceptorFn } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

// Set by the backend's DatabaseSetupInterceptor. Needed to tell "database setup required" apart from
// the other things that also respond 428 (e.g. the AI assistant not being configured), which must not
// redirect anywhere.
const DB_SETUP_REQUIRED_HEADER = 'X-Kafkador-Db-Setup-Required';

export const SessionInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

    return next(req).pipe(
      catchError(error => {
        if (error.status === 428 && error.headers?.get(DB_SETUP_REQUIRED_HEADER) === 'true') {
          router.navigate(['/setup']);
        } else if (error.status === 401) {
          console.warn('⚠ Unauthorized - redirecting to ConnectComponent...');
          router.navigate(['/connect']);
        }
        return throwError(() => error);
      })
    );
};
