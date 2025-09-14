import { Component, inject  } from '@angular/core';
import { Router } from '@angular/router';
import { GenericResponse } from '../../models/generic-response';
import { Connection } from '../../models/connection';
import { ApiService } from '../../services/api.service';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-connect',
  imports: [CommonModule],
  templateUrl: './connect.component.html',
  styleUrl: './connect.component.scss'
})
export class ConnectComponent  {

  connections: Connection[] = [];
  baseUrl = environment.baseUrl;
  router = inject(Router);

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.apiService.getConnections().subscribe((res: GenericResponse<Connection[]>) => {
      this.connections = res.data;
    });
  }

  connect(id:string){
    this.apiService.connect(id).subscribe((res: GenericResponse<Connection>) => {
      console.log(res.data);
      this.router.navigate(['/cluster']);
    });
  }

}
