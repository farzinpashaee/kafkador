import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { ApiService, ValidationService, CommonService } from '../../services';
import { GenericResponse, Connection, Error } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';

@Component({
  selector: 'app-connections',
  imports: [CommonModule,RouterModule,FormsModule,PaginationComponent],
  templateUrl: './connections.component.html',
  styleUrl: './connections.component.scss'
})
export class ConnectionsComponent {

  connections: Connection[] = [];
  editConnection: Connection = { id:'', clusterId:'', name: '', host: '', port: '' };
  deletedConnection: Connection = { id:'', clusterId:'', name: '', host: '', port: '' };
  isLoading: boolean = true;
  readonly pageSize = 10;
  page = 1;

  get pagedConnections(): Connection[] {
    return this.connections.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.getConnections();
  }

  getConnections() {
    this.apiService.getConnections().subscribe((res: HttpResponse<GenericResponse<Connection[]>>) => {
      this.connections = res.body?.data ?? [];
      this.isLoading = false;
    });
  }

  openEditDialog(connection: Connection) {
    this.errors.delete('editConnection');
    this.editConnection = { ...connection };
  }

  updateConnection(){
    const errors = this.validationService.validateRequiredFields(this.editConnection, ['name', 'host', 'port']);
    if (errors.length > 0) {
      this.errors.set("editConnection",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('editConnection');
    }
    this.flags.set('editConnectionLoading',true);
    this.apiService.updateConnection(this.editConnection.id, this.editConnection).subscribe({
      next: (res: HttpResponse<GenericResponse<Connection>>) => {
        const updated = res.body?.data ?? this.editConnection;
        const index = this.connections.findIndex(c => c.id === updated.id);
        if (index !== -1) this.connections[index] = updated;
        this.flags.set('editConnectionLoading',false);
        this.commonService.hideModal('editConnectionModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("editConnection",this.commonService.prepareError(res.error.error,'500','Failed to update connection!'));
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
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.connections.length / this.pageSize)));
        this.flags.set('deleteConnectionLoading',false);
        this.commonService.hideModal('deleteConnectionModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("deleteConnection",this.commonService.prepareError(res.error.error,'500','Failed to delete connection!'));
        this.flags.set('deleteConnectionLoading',false);
      }
    });
  }

}
