import { Component, inject  } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { LocalStorageService } from '../../services/local-storage.service';
import { GenericResponse } from '../../models/generic-response';
import { Connection } from '../../models/connection';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-connect',
  imports: [CommonModule,FormsModule],
  templateUrl: './connect.component.html',
  styleUrl: './connect.component.scss'
})
export class ConnectComponent  {

  connections: Connection[] = [];
  connection!: Connection;
  newConnection!: Connection;
  baseUrl = environment.baseUrl;
  router = inject(Router);

  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService) {}

  ngOnInit() {
    this.newConnection = { id:'', name: '', host: '', port: '' };
    this.apiService.getConnections().subscribe((res: GenericResponse<Connection[]>) => {
      this.connections = res.data;
    });
  }

  connect(id:string){
    this.apiService.connect(id).subscribe((res: GenericResponse<Connection>) => {
      this.connection = res.data;
      this.localStorageService.setItem('activeConnection', this.connection);
      this.router.navigate(['/']);
    });
  }

  addConnection(){
    this.apiService.addConnection(this.newConnection).subscribe((res: GenericResponse<Connection>) => {
      this.connections.push(res.data);
    });
  }

  clearForm(){
      this.newConnection = { id:'', name: '', host: '', port: '' };
  }

}
