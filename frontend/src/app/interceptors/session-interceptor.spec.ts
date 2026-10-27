import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpClient, HttpHeaders, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SessionInterceptor } from './session-interceptor';

describe('SessionInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([SessionInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  function fail(url: string, status: number, headers: Record<string, string> = {}) {
    http.get(url).subscribe({ error: () => {} });
    backend.expectOne(url).flush(null, { status, statusText: 'x', headers: new HttpHeaders(headers) });
  }

  it('sends a signed-out browser to the sign-in page', () => {
    fail('/api/v1/topics', 401, { 'X-Kafkador-Login-Required': 'true' });
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sends a browser without a cluster connection to Connect', () => {
    fail('/api/v1/topics', 401);
    expect(router.navigate).toHaveBeenCalledWith(['/connect']);
  });

  it('sends unfinished setup to the setup page', () => {
    fail('/api/v1/topics', 428, { 'X-Kafkador-Db-Setup-Required': 'true' });
    expect(router.navigate).toHaveBeenCalledWith(['/setup']);
  });

  it('leaves a wrong password to the sign-in page', () => {
    fail('/api/v1/auth/login', 401);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
