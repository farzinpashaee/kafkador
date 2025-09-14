import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpInterceptorFn } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

export const SessionInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

    return next(req).pipe(
      catchError(error => {
        if (error.status === 400) {
          console.warn('⚠️ Unauthorized - redirecting to ConnectComponent...');
          router.navigate(['/connect']); // 👈 route to your ConnectComponent
        }
        return throwError(() => error);
      })
    );
};
