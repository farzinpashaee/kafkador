import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AboutComponent } from './about.component';

describe('AboutComponent', () => {
  let fixture: ComponentFixture<AboutComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AboutComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AboutComponent);
    fixture.detectChanges();
  });

  function version(): string | undefined {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="about-version"]')?.textContent?.trim();
  }

  it('shows the version the backend was built as', () => {
    http.expectOne(r => r.url.endsWith('/api/v1/app-info')).flush({ data: { version: '1.0.0-beta.1' } });
    expect(version()).toBe('1.0.0-beta.1');
  });

  it('says the version is unknown when the backend cannot say', () => {
    http.expectOne(r => r.url.endsWith('/api/v1/app-info')).flush(null, { status: 500, statusText: 'Server Error' });
    expect(version()).toBe('unknown');
  });

  it('links to codestreamlab.com in a new tab', () => {
    const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a'))
      .filter(a => a.href.startsWith('https://codestreamlab.com'));
    expect(links.length).toBeGreaterThan(0);
    links.forEach(link => {
      expect(link.target).toBe('_blank');
      expect(link.rel).toContain('noopener');
    });
  });
});
