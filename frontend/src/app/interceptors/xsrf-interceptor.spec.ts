import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { XsrfInterceptor } from './xsrf-interceptor';

describe('XsrfInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([XsrfInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    document.cookie = 'XSRF-TOKEN=token-123; path=/';
  });

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    backend.verify();
  });

  it('sends the cookie\'s token with requests that change something, to another port too', () => {
    http.post('http://localhost:8080/api/v1/topics', {}).subscribe();
    http.delete('/api/v1/topics/orders').subscribe();

    expect(backend.expectOne('http://localhost:8080/api/v1/topics').request.headers.get('X-XSRF-TOKEN')).toBe('token-123');
    expect(backend.expectOne('/api/v1/topics/orders').request.headers.get('X-XSRF-TOKEN')).toBe('token-123');
  });

  it('leaves reads alone', () => {
    http.get('/api/v1/topics').subscribe();

    expect(backend.expectOne('/api/v1/topics').request.headers.has('X-XSRF-TOKEN')).toBeFalse();
  });
});
