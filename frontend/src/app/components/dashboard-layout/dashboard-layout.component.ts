import { Component, inject, DOCUMENT } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from '../../services/local-storage.service';
import { ApiService } from '../../services/api.service';
import { Connection } from '../../models/connection';
import { GenericResponse } from '../../models/generic-response';
import { BreadcrumbComponent } from '../../components/breadcrumb/breadcrumb.component';
import { Config } from '../../models/config';

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

  constructor(private route: ActivatedRoute,
              private apiService: ApiService,
              private localStorageService: LocalStorageService) {}

  ngOnInit() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    this.document.body.setAttribute('data-bs-theme', savedTheme);
    this.isDark = savedTheme === 'dark';

    const activeConnectionFromStorage = this.localStorageService.getItem('activeConnection');
    if (activeConnectionFromStorage !== null) {
      this.activeConnection = activeConnectionFromStorage as Connection;
      this.connectedHost = this.activeConnection.host;
    }
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



}
