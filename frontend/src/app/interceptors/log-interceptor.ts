import { HttpInterceptorFn } from '@angular/common/http';
import { tap, catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

export const LogInterceptor: HttpInterceptorFn = (req, next) => {
  const startTime = Date.now();

  console.log(`🚀 [HTTP Request] ${req.method} ${req.url}`);

  if (req.body) {
    console.log('📤 Request Body:', req.body);
  }

  if (req.headers.keys().length > 0) {
    console.log('📋 Request Headers:', req.headers);
  }

  return next(req).pipe(
    tap(event => {
      const duration = Date.now() - startTime;
      console.log(`✅ [HTTP Response] ${req.method} ${req.url} (${duration}ms)`);
      console.log('📥 Response:', event);
    }),
    catchError(error => {
      const duration = Date.now() - startTime;
      console.error(`❌ [HTTP Error] ${req.method} ${req.url} (${duration}ms)`);
      console.error('Error details:', error);
      return throwError(() => error);
    })
  );
};
