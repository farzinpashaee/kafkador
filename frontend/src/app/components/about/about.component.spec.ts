import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AboutComponent } from './about.component';
import { APP_VERSION } from '../../app-info';

describe('AboutComponent', () => {
  let fixture: ComponentFixture<AboutComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AboutComponent] }).compileComponents();
    fixture = TestBed.createComponent(AboutComponent);
    fixture.detectChanges();
  });

  it('shows the version and links to codestreamlab.com in a new tab', () => {
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('[data-testid="about-version"]')?.textContent?.trim()).toBe(APP_VERSION);
    const links = Array.from(page.querySelectorAll('a')).filter(a => a.href.startsWith('https://codestreamlab.com'));
    expect(links.length).toBeGreaterThan(0);
    links.forEach(link => {
      expect(link.target).toBe('_blank');
      expect(link.rel).toContain('noopener');
    });
  });
});
