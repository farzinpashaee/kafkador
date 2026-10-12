import { Component, inject  } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ApiService, ValidationService, LocalStorageService, CommonService } from '../../services';
import { GenericResponse, Connection, Error, ClusterCredentials, CREDENTIALS_REQUIRED, blankCredentials } from '../../models';
import { ClusterCredentialsFormComponent } from '../cluster-credentials-form/cluster-credentials-form.component';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-connect',
  imports: [CommonModule,FormsModule,ClusterCredentialsFormComponent],
  templateUrl: './connect.component.html',
  styleUrl: './connect.component.scss'
})
export class ConnectComponent  {

  connections: Connection[] = [];
  connection!: Connection;
  newConnection!: Connection;
  editConnection!: Connection;
  deletedConnection!: Connection;
  baseUrl = environment.baseUrl;
  router = inject(Router);
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  /** The saved connection being connected; drives the per-row spinner and the sign-in dialog. */
  connectingTo?: Connection;
  /** Sign-in dialog shown when connecting needs credentials. */
  signInCredentials: ClusterCredentials = blankCredentials();
  signInMessage = '';
  // "This cluster requires authentication" switches and their fields in the add / edit dialogs.
  newConnectionAuth = false;
  newCredentials: ClusterCredentials = blankCredentials();
  editConnectionAuth = false;
  editCredentials: ClusterCredentials = blankCredentials();

  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService,
    private commonService: CommonService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.newConnection = { id:'', clusterId:'', name: '', host: '', port: '9092' };
    this.editConnection = { id:'', clusterId:'', name: '', host: '', port: '' };
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
        error: (res:HttpErrorResponse) => {
          this.errors.set("getConnections",this.commonService.prepareError(res.error.error,'500','Failed to get cluster connections!'));
          this.flags.set('getConnectionsLoading',false);
        }
      });
  }

  /** Connects a saved connection; a cluster that needs credentials opens the sign-in dialog instead. */
  connect(connection: Connection){
    this.errors.delete('connect');
    this.connectingTo = connection;
    this.apiService.connect(connection.id).subscribe({
      next: (res: GenericResponse<Connection>) => this.connected(res.data),
      error: (res: HttpErrorResponse) => {
        if (this.isCredentialsRequired(res)) {
          this.signInCredentials = blankCredentials();
          this.signInMessage = res.error.error.message;
          this.errors.delete('signIn');
          this.commonService.showModal('clusterSignInModal');
        } else {
          this.errors.set('connect', this.commonService.prepareError(res.error?.error, '500', `Failed to connect to '${connection.name}'!`));
          this.connectingTo = undefined;
        }
      }
    });
  }

  /** Retries the connection with the credentials from the sign-in dialog. */
  signIn(){
    if (!this.connectingTo) return;
    const errors = this.validationService.validateRequiredFields(this.signInCredentials, ['username', 'password']);
    if (errors.length > 0) {
      this.errors.set('signIn', {code:'400', message: errors[0], datetime:''});
      return;
    }
    this.errors.delete('signIn');
    this.flags.set('signInLoading', true);
    this.apiService.connect(this.connectingTo.id, this.signInCredentials).subscribe({
      next: (res: GenericResponse<Connection>) => {
        this.flags.set('signInLoading', false);
        this.commonService.hideModal('clusterSignInModal');
        this.connected(res.data);
      },
      error: (res: HttpErrorResponse) => {
        this.flags.set('signInLoading', false);
        this.errors.set('signIn', this.commonService.prepareError(res.error?.error, '500', 'Failed to connect with these credentials!'));
      }
    });
  }

  cancelSignIn(){
    this.connectingTo = undefined;
    this.signInCredentials = blankCredentials();
  }

  private connected(connection: Connection){
    this.signInCredentials = blankCredentials();
    this.connection = connection;
    this.connectingTo = undefined;
    this.localStorageService.setItem('activeConnection', this.connection);
    this.router.navigate(['/']);
  }

  private isCredentialsRequired(res: HttpErrorResponse): boolean {
    return res.status === 403 && res.error?.error?.code === CREDENTIALS_REQUIRED;
  }

  addConnection(){
    const errors = this.validationService.validateRequiredFields(this.newConnection, ['name', 'host', 'port']);
    if (errors.length > 0) {
      this.errors.set("addConnection",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('addConnection');
    }
    if (this.newConnectionAuth) {
      const missing = this.validationService.validateRequiredFields(this.newCredentials, ['username', 'password']);
      if (missing.length > 0) {
        this.errors.set("addConnection",{code:'400',message:missing[0],datetime:''});
        return;
      }
    }
    this.flags.set('addConnectionLoading',true);
    const request = { ...this.newConnection, credentials: this.newConnectionAuth ? this.newCredentials : undefined };
    this.apiService.addConnection(request).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        this.connections.push(res.body?.data ?? { ...this.newConnection, credentials: undefined });
        this.newCredentials = blankCredentials();
        this.flags.set('addConnectionLoading',false);
        this.flags.set('getConnectionsEmpty',false);
        this.commonService.hideModal('addClusterModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("addConnection",this.commonService.prepareError(res.error?.error,'500','Failed to add new connection!'));
        if (this.isCredentialsRequired(res)) this.newConnectionAuth = true;
        this.flags.set('addConnectionLoading',false);
      }
    });
  }

  clearForm(){
      this.newConnection = { id:'', clusterId:'', name: '', host: '', port: '' };
      this.newConnectionAuth = false;
      this.newCredentials = blankCredentials();
      this.errors.delete('addConnection');
  }

  openEditDialog(connection: Connection) {
    this.errors.delete('editConnection');
    this.editConnection = { ...connection };
    this.editConnectionAuth = false;
    this.editCredentials = blankCredentials();
  }

  updateConnection(){
    const errors = this.validationService.validateRequiredFields(this.editConnection, ['name', 'host', 'port']);
    if (errors.length > 0) {
      this.errors.set("editConnection",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('editConnection');
    }
    if (this.editConnectionAuth) {
      const missing = this.validationService.validateRequiredFields(this.editCredentials, ['username', 'password']);
      if (missing.length > 0) {
        this.errors.set("editConnection",{code:'400',message:missing[0],datetime:''});
        return;
      }
    }
    this.flags.set('editConnectionLoading',true);
    const request = { ...this.editConnection, credentials: this.editConnectionAuth ? this.editCredentials : undefined };
    this.apiService.updateConnection(this.editConnection.id, request).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        const updated = res.body?.data ?? this.editConnection;
        const index = this.connections.findIndex(c => c.id === updated.id);
        if (index !== -1) this.connections[index] = updated;
        this.flags.set('editConnectionLoading',false);
        this.commonService.hideModal('editClusterModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("editConnection",this.commonService.prepareError(res.error?.error,'500','Failed to update connection!'));
        if (this.isCredentialsRequired(res)) this.editConnectionAuth = true;
        this.flags.set('editConnectionLoading',false);
      }
    });
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
        error: (res:HttpErrorResponse) => {
          this.errors.set("deleteConnection",this.commonService.prepareError(res.error.error,'500','Failed to delete connection!'));
          this.flags.set('deleteConnectionLoading',false);
        }
      });
  }

}
