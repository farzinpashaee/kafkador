import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { GenericResponse } from '../../models/generic-response';
import { Connection } from '../../models/connection';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-connections',
  imports: [CommonModule,RouterModule],
  templateUrl: './connections.component.html',
  styleUrl: './connections.component.scss'
})
export class ConnectionsComponent {

  connections: Connection[] = [];
  isLoading: boolean = true;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.apiService.getConnections().subscribe((res: GenericResponse<Connection[]>) => {
      this.connections = res.data;
      this.isLoading = false;
    });
  }

}
