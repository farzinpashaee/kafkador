import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { ApiService } from '../../services';
import { GenericResponse, Connection } from '../../models';

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
    this.apiService.getConnections().subscribe((res: HttpResponse<GenericResponse<Connection[]>>) => {
      this.connections = res.body?.data ?? [];
      this.isLoading = false;
    });
  }

}
