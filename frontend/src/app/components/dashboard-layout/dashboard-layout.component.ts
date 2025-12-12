import { Component, inject, DOCUMENT } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil } from 'rxjs';
import { RouterModule, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from '../../services/local-storage.service';
import { ApiService } from '../../services/api.service';
import { CommonService } from '../../services/common.service';
import { Connection } from '../../models/connection';
import { GenericResponse } from '../../models/generic-response';
import { SearchResult } from '../../models/search-result';
import { Config } from '../../models/config';
import { BreadcrumbComponent } from '../../components/breadcrumb/breadcrumb.component';


@Component({
  selector: 'app-dashboard-layout',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CommonModule, BreadcrumbComponent,FormsModule],
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
  commonModalBody!: string;

  isSearching = false;
  isSearchLoading = false;
  searchQuery: string = "";
  searchResults!: SearchResult[];

  constructor(private route: ActivatedRoute,
              private apiService: ApiService,
              private commonService: CommonService,
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
    this.commonModalBody=`<div class="spinner-border spinner-border-sm" role="status">
                                      <span class="visually-hidden">Loading...</span>
                                    </div>&nbsp; Disconnecting...`;
    this.commonService.showCommonModal();
    setTimeout(() => {this.apiService.disconnect().subscribe({
      next: async (res: HttpResponse<GenericResponse<Connection>>) => {
        this.localStorageService.removeItem('activeConnection');
        this.commonService.hideCommonModal();
        this.router.navigate(['/connect']);
      },
      error: async (err) => {
        this.commonService.hideCommonModal();
        this.router.navigate(['/connect']);
      }
    });
    },1000);
  }

  toggleTheme() {
    this.isDark = !this.isDark;
    const theme = this.isDark ? 'dark' : 'light';
    this.document.body.setAttribute('data-bs-theme', theme);
    localStorage.setItem('theme', theme);
  }

  onSearchInputChange (event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.searchQuery = value;
    this.searchSubject.next(value);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  goTo(route: string) {
    this.isSearching = false;
    this.searchQuery = "";
    this.router.navigate([route]);
  }

}
