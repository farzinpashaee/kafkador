import { HttpInterceptorFn } from '@angular/common/http';
import { tap, catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { environment } from '../environments/environment';

export const LogInterceptor: HttpInterceptorFn = (req, next) => {
  if (environment.production) {
    return next(req);
  }

  const startTime = Date.now();

  console.log(`[HTTP Request] ${req.method} ${req.url}`);

  return next(req).pipe(
    tap(() => {
      const duration = Date.now() - startTime;
      console.log(`[HTTP Response] ${req.method} ${req.url} (${duration}ms)`);
    }),
    catchError(error => {
      const duration = Date.now() - startTime;
      console.error(`[HTTP Error] ${req.method} ${req.url} (${duration}ms)`, error);
      return throwError(() => error);
    })
  );
};
