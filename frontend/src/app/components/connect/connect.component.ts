import { Component, inject  } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { ValidationService } from '../../services/validation.service';
import { LocalStorageService } from '../../services/local-storage.service';
import { CommonService } from '../../services/common.service';
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
  deletedConnection!: Connection;
  baseUrl = environment.baseUrl;
  router = inject(Router);
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService,
    private commonService: CommonService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.newConnection = { id:'', clusterId:'', name: '', host: '', port: '9092' };
    this.deletedConnection = { id:'', clusterId:'', name: '', host: '', port: '' };
    this.flags.set('getConnectionsEmpty',false);
    this.getConnections();
  }

  getConnections(){
    this.errors.delete("getConnections");
    this.flags.set('getConnectionsLoading',true);
    this.apiService.getConnections().subscribe({ next: (res: HttpResponse<GenericResponse<Connection[]>>) => {
          this.connections = res.body?.data ?? [];
          if(this.connections.length==0){
             this.flags.set('getConnectionsEmpty',true);
          }
          this.flags.set('getConnectionsLoading',false);
        },
        error: (err:HttpErrorResponse) => {
          this.errors.set("getConnections",{code:'500',message:err.error.message});
          this.flags.set('getConnectionsLoading',false);
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
      this.errors.set("addConnection",{code:'400',message:errors[0]});
      return;
    } else {
      this.errors.delete('addConnection');
    }
    this.flags.set('addConnectionLoading',true);
    this.apiService.addConnection(this.newConnection).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        this.connections.push(this.newConnection);
        this.flags.set('addConnectionLoading',false);
        this.flags.set('getConnectionsEmpty',false);
        this.commonService.hideModal('addClusterModal');
      },
      error: (err:HttpErrorResponse) => {
        this.errors.set("addConnection",{code:'500',message:err.error.message});
        this.flags.set('addConnectionLoading',false);
      }
    });
  }

  clearForm(){
      this.newConnection = { id:'', clusterId:'', name: '', host: '', port: '' };
      this.errors.delete('addConnection');
  }

  openDeleteDialog(connection: Connection) {
    this.errors.delete('deleteConnection');
    this.deletedConnection = connection;
  }

  deleteConnection() {
    if (!this.deletedConnection) return;
    this.flags.set('deleteConnectionLoading',true);
    this.apiService.deleteConnection(this.deletedConnection.id).subscribe({
        next: (res: HttpResponse<void>) => {
          this.connections = this.connections.filter(c => c.id !== this.deletedConnection.id);
          this.flags.set('deleteConnectionLoading',false);
          this.commonService.hideModal('deleteClusterModal');
        },
        error: (err:HttpErrorResponse) => {
          this.errors.set("deleteConnection",{code:'500',message:err.error.message});
          this.flags.set('deleteConnectionLoading',false);
        }
      });
  }

}
