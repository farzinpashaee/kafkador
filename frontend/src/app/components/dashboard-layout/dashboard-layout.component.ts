import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService } from '../../services/local-storage.service';
import { ApiService } from '../../services/api.service';
import { Connection } from '../../models/connection';
import { GenericResponse } from '../../models/generic-response';

@Component({
  selector: 'app-dashboard-layout',
  imports: [RouterOutlet, RouterModule, CommonModule],
  templateUrl: './dashboard-layout.component.html',
  styleUrl: './dashboard-layout.component.scss'
})
export class DashboardLayoutComponent {

  connectedHost = "Active Host" ;
  activeConnection!: Connection ;
  router = inject(Router);

  constructor(private route: ActivatedRoute,
              private apiService: ApiService,
              private localStorageService: LocalStorageService) {}

  ngOnInit() {
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
}
