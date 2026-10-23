import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { DashboardLayoutComponent } from './dashboard-layout.component';

describe('DashboardLayoutComponent', () => {
  let component: DashboardLayoutComponent;
  let fixture: ComponentFixture<DashboardLayoutComponent>;

  function create() {
    fixture = TestBed.createComponent(DashboardLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    localStorage.removeItem('sidebarCollapsed');
    await TestBed.configureTestingModule({
      imports: [DashboardLayoutComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    })
    .compileComponents();
    create();
  });

  afterEach(() => localStorage.removeItem('sidebarCollapsed'));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('collapses the menu to its icons, with each label as a tooltip', () => {
    const page: HTMLElement = fixture.nativeElement;
    const topics = Array.from(page.querySelectorAll<HTMLAnchorElement>('.sidebar a.nav-link'))
      .find(link => link.textContent?.includes('Topics'))!;
    expect(page.querySelector('.container-fluid')!.classList).not.toContain('sidebar-collapsed');
    expect(topics.getAttribute('title')).toBeNull();

    (page.querySelector('.sidebar-toggle') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(page.querySelector('.container-fluid')!.classList).toContain('sidebar-collapsed');
    expect(page.querySelector('.navbar-brand')!.classList).toContain('brand-collapsed');
    expect(topics.getAttribute('title')).toBe('Topics');
    expect(page.querySelector('.sidebar-toggle')!.getAttribute('aria-expanded')).toBe('false');
  });

  it('puts the toggle in the header next to the logo, as an icon', () => {
    const toggle = (fixture.nativeElement as HTMLElement).querySelector('header .sidebar-toggle')!;
    expect(toggle.previousElementSibling!.classList).toContain('navbar-brand');
    expect(toggle.textContent!.trim()).toBe('');
    expect(toggle.getAttribute('aria-label')).toBe('Collapse menu');
  });

  it('remembers the choice for the next visit', () => {
    component.toggleSidebar();
    expect(localStorage.getItem('sidebarCollapsed')).toBe('true');

    fixture.destroy();
    create();
    expect(component.sidebarCollapsed).toBeTrue();

    component.toggleSidebar();
    expect(localStorage.getItem('sidebarCollapsed')).toBe('false');
  });
});
