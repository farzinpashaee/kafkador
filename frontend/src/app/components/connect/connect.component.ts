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
import { Error } from '../../models/error';
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
  errors: Map<string, Error> = new Map();
  loadings: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.newConnection = { id:'', name: '', host: '', port: '' };
    this.getConnections();
  }

  getConnections(){
    this.errors.delete("getConnections");
    this.loadings.set('getConnections',true);
    this.apiService.getConnections().subscribe({ next: (res: HttpResponse<GenericResponse<Connection[]>>) => {
            this.connections = res.body?.data ?? [];
          this.loadings.set('getConnections',false);
        },
        error: (err) => {
          this.errors.set("getConnections",{code:'500',message:err.message});
          this.loadings.set('getConnections',false);
        }
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
      console.log("------------------")
      this.errors.set("addConnection",{code:'400',message:errors[0]});
      return;
    } else {
      this.errors.delete('addConnection');
    }
    this.loadings.set('addConnection',true);
    this.apiService.addConnection(this.newConnection).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        this.connections.push(this.newConnection);
        this.loadings.set('addConnection',false);
      },
      error: (err) => {
        this.errors.set("addConnection",{code:'500',message:err.message});
        this.loadings.set('addConnection',false);
      }
    });
  }

  clearForm(){
      this.newConnection = { id:'', name: '', host: '', port: '' };
      this.errors.delete('addConnection');
  }

}
