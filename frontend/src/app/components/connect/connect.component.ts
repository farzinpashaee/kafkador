import { Component, inject  } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { ValidationService } from '../../services/validation.service';
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
  newConnectionError!: String;
  newConnectionLoading: Boolean = false;
  baseUrl = environment.baseUrl;
  router = inject(Router);

  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService,
    private validationService: ValidationService) {}

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
    const errors = this.validationService.validateRequiredFields(this.newConnection, ['name', 'host', 'port']);
    if (errors.length > 0) {
      this.newConnectionError = "<ul><li>"+errors.join('</li><li>')+"</li></ul>";
      return;
    } else {
      this.newConnectionError = "";
    }
    this.newConnectionLoading = true;
    this.apiService.addConnection(this.newConnection).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        this.connections.push(this.newConnection);
        this.newConnectionLoading = false;
      },
      error: (err) => {
        this.newConnectionError = err.status;
        this.newConnectionLoading = false;
      }
    });
  }

  clearForm(){
      this.newConnection = { id:'', name: '', host: '', port: '' };
      this.newConnectionError = "";
  }

}
