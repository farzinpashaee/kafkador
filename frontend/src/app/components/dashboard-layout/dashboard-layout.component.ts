import { Component, inject, DOCUMENT } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';
import { RouterModule, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from '../../services/local-storage.service';
import { ApiService } from '../../services/api.service';
import { Connection } from '../../models/connection';
import { GenericResponse } from '../../models/generic-response';
import { SearchResult } from '../../models/search-result';
import { Config } from '../../models/config';
import { BreadcrumbComponent } from '../../components/breadcrumb/breadcrumb.component';


@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, BreadcrumbComponent],
  templateUrl: './dashboard-layout.component.html',
  styleUrl: './dashboard-layout.component.scss'
})
export class DashboardLayoutComponent {

  connectedHost = "Active Host" ;
  activeConnection!: Connection ;
  router = inject(Router);
  document = inject(DOCUMENT);
  isDark = false;
  documentation!: string;

  isSearching = false;
  isSearchLoading = false;
  searchQuery: string = "";
  searchResults!: SearchResult[];

  constructor(private route: ActivatedRoute,
              private apiService: ApiService,
              private localStorageService: LocalStorageService) {}

  private searchSubject = new Subject<string>();
  private destroy$ = new Subject<void>();

  ngOnInit() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    this.document.body.setAttribute('data-bs-theme', savedTheme);
    this.isDark = savedTheme === 'dark';

    const activeConnectionFromStorage = this.localStorageService.getItem('activeConnection');
    if (activeConnectionFromStorage !== null) {
      this.activeConnection = activeConnectionFromStorage as Connection;
      this.connectedHost = this.activeConnection.host;
    }

    this.searchSubject
          .pipe(
            debounceTime(500),              // wait 400ms after typing stops
            distinctUntilChanged(),          // only emit if value changed
            switchMap(query => {
              this.isSearching = true;
              this.isSearchLoading = true;
              return this.apiService.search(query);
            }),
            takeUntil(this.destroy$)
          )
          .subscribe((res: GenericResponse<SearchResult[]>) => {
            this.searchResults = res.data;
            this.isSearchLoading = false;
          });
  }

  disconnect(){
    this.apiService.disconnect().subscribe((res: GenericResponse<Connection>) => {
      this.localStorageService.removeItem('activeConnection');
      this.router.navigate(['/connect']);
    });
  }

  toggleTheme() {
      console.log("xxxxxxxxxxxxxxx");
      this.isDark = !this.isDark;
      const theme = this.isDark ? 'dark' : 'light';
      this.document.body.setAttribute('data-bs-theme', theme);
      localStorage.setItem('theme', theme);
  }

  onSearchInputChange (event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.searchQuery = value;
    this.searchSubject.next(value); // trigger the stream
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

}
