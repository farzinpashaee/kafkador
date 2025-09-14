import { Component } from '@angular/core';
import { RouterModule, RouterOutlet, ActivatedRoute } from '@angular/router';
import { LocalStorageService } from '../../services/local-storage.service';
import { Connection } from '../../models/connection';

@Component({
  selector: 'app-dashboard-layout',
  imports: [RouterOutlet, RouterModule],
  templateUrl: './dashboard-layout.component.html',
  styleUrl: './dashboard-layout.component.scss'
})
export class DashboardLayoutComponent {

  connectedHost = "Active Host" ;
  activeConnection!: Connection ;

  constructor(private route: ActivatedRoute,
              private localStorageService: LocalStorageService) {}

  ngOnInit() {
    const activeConnectionFromStorage = this.localStorageService.getItem('activeConnection');
    if (activeConnectionFromStorage !== null) {
      this.activeConnection = activeConnectionFromStorage as Connection;
      this.connectedHost = this.activeConnection.host;
    }
  }

}
