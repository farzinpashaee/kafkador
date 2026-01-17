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
        const breadcrumbs = this.buildBreadcrumbs(root);
        this.breadcrumbs$.next(breadcrumbs);
      });
  }

  breadcrumbs() {
    return this.breadcrumbs$.asObservable();
  }

 private buildBreadcrumbs(
   route: ActivatedRouteSnapshot,
   url: string = '',
   breadcrumbs: Breadcrumb[] = []
 ): Breadcrumb[] {

   const routeSegment = route.url.map(s => s.path).join('/');
   if (routeSegment) {
     url += `/${routeSegment}`;
   }

   const breadcrumbData = route.data['breadcrumb'];

   if (breadcrumbData) {
     let label = '';
     let breadcrumbUrl = url;

     // ✅ Static breadcrumb object
     if (breadcrumbData.label) {
       label = breadcrumbData.label;
       breadcrumbUrl = breadcrumbData.url ?? url;
     }

     // ✅ Dynamic param breadcrumb
     if (breadcrumbData.param && route.params[breadcrumbData.param]) {
       label = this.formatLabel(route.params[breadcrumbData.param]);
     }

     breadcrumbs.push({
       label,
       url: breadcrumbUrl
     });
   }

   if (route.firstChild) {
     return this.buildBreadcrumbs(route.firstChild, url, breadcrumbs);
   }

   return breadcrumbs;
 }


  private formatLabel(value: string): string {
    return value
      .replace(/-/g, ' ')
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
