import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, filter } from 'rxjs';

export interface Breadcrumb {
  label: string;
  url: string;
}

@Injectable({
  providedIn: 'root'
})
export class BreadcrumbService {
private breadcrumbs$ = new BehaviorSubject<Breadcrumb[]>([]);

  constructor(private router: Router) {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        const root = this.router.routerState.snapshot.root;
        const breadcrumbs: Breadcrumb[] = this.getBreadcrumbs(root);
        this.breadcrumbs$.next(breadcrumbs);
      });
  }

  getBreadcrumbs(route: ActivatedRouteSnapshot, url: string = '', breadcrumbs: Breadcrumb[] = []): Breadcrumb[] {
    if (route.data['breadcrumb']) {
      const breadcrumb: Breadcrumb = {
        label: route.data['breadcrumb'],
        url: url + '/' + route.url.map(segment => segment.path).join('/')
      };
      breadcrumbs.push(breadcrumb);
    }

    if (route.firstChild) {
      return this.getBreadcrumbs(route.firstChild, breadcrumbs[breadcrumbs.length - 1]?.url || '', breadcrumbs);
    }
    return breadcrumbs;
  }

  breadcrumbs() {
    return this.breadcrumbs$.asObservable();
  }
}
