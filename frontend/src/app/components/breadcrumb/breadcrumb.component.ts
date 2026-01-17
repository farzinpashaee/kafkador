import { Component } from '@angular/core';
import { Router, NavigationEnd, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule   // ✅ REQUIRED for routerLink
  ],
  templateUrl: './breadcrumb.component.html'
})
export class BreadcrumbComponent {
  breadcrumbs: { label: string; url: string }[] = [];

  constructor(private router: Router) {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe(() => {
        this.buildFromUrl(this.router.url);
      });
  }

  private buildFromUrl(url: string): void {
    const segments = url
      .split('?')[0]
      .split('#')[0]
      .split('/')
      .filter(Boolean);

    let accumulatedUrl = '';
    this.breadcrumbs = segments.map(segment => {
      accumulatedUrl += `/${segment}`;
      return {
        label: segment,
        url: accumulatedUrl
      };
    });
  }

  private formatLabel(segment: string): string {
    return segment
      .replace(/-/g, ' ')
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
