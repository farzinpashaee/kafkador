import { HttpInterceptorFn } from '@angular/common/http';

// The backend (SecurityConfig) sets this cookie and wants it back in this header on every request that changes
// something. Only pages on Kafkador's own site can read the cookie, so another site can't make those requests.
const XSRF_COOKIE = 'XSRF-TOKEN';
const XSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS'];

/**
 * Angular adds this header itself only for relative URLs, but the dev build calls the API on another port (the cookie
 * is still readable there: cookies don't depend on the port).
 */
export const XsrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (SAFE_METHODS.includes(req.method) || req.headers.has(XSRF_HEADER)) {
    return next(req);
  }
  const token = readCookie(XSRF_COOKIE);
  return next(token ? req.clone({ setHeaders: { [XSRF_HEADER]: token } }) : req);
};

function readCookie(name: string): string | null {
  const prefix = name + '=';
  for (const part of document.cookie.split(';')) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) return decodeURIComponent(cookie.substring(prefix.length));
  }
  return null;
}
