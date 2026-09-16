import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { GenericResponse, KsqlDbConfig, KsqlServerInfo, KsqlStream, KsqlTable, KsqlQuery, Error } from '../../models';

@Component({
  selector: 'app-ksql-db',
  imports: [CommonModule, RouterLink],
  templateUrl: './ksql-db.component.html',
  styleUrl: './ksql-db.component.scss'
})
export class KsqlDBComponent implements OnInit {

  config: KsqlDbConfig = { configured: false };
  serverInfo!: KsqlServerInfo;
  streams: KsqlStream[] = [];
  tables: KsqlTable[] = [];
  queries: KsqlQuery[] = [];
  terminatedQuery: KsqlQuery = { id: '', queryType: '' };

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    this.getConfig();
  }

  getConfig() {
    this.errors.delete('getConfig');
    this.flags.set('configLoading', true);
    this.apiService.getKsqlDbConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlDbConfig>>) => {
        this.config = res.body?.data ?? { configured: false };
        this.flags.set('configLoading', false);
        if (this.config.configured) this.loadData();
      },
      error: (res:HttpErrorResponse) => {
        if (res.status === 404) {
          this.config = { configured: false };
        } else {
          this.errors.set('getConfig', this.commonService.prepareError(res.error.error,'500','Failed to load ksqlDB configuration!'));
        }
        this.flags.set('configLoading', false);
      }
    });
  }

  loadData() {
    this.errors.delete('loadData');
    this.flags.set('dataLoading', true);

    this.apiService.getKsqlDbInfo().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlServerInfo>>) => { this.serverInfo = res.body!.data; },
      error: (res:HttpErrorResponse) => {
        this.errors.set('loadData', this.commonService.prepareError(res.error.error,'500','Failed to reach the ksqlDB server!'));
      }
    });

    this.apiService.getKsqlDbStreams().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlStream[]>>) => {
        this.streams = res.body?.data ?? [];
        this.flags.set('dataLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('loadData', this.commonService.prepareError(res.error.error,'500','Failed to reach the ksqlDB server!'));
        this.flags.set('dataLoading', false);
      }
    });

    this.apiService.getKsqlDbTables().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlTable[]>>) => { this.tables = res.body?.data ?? []; },
      error: () => {}
    });

    this.apiService.getKsqlDbQueries().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlQuery[]>>) => { this.queries = res.body?.data ?? []; },
      error: () => {}
    });
  }

  openTerminateDialog(query: KsqlQuery) {
    this.errors.delete('terminateQuery');
    this.terminatedQuery = query;
  }

  terminateQuery() {
    if (!this.terminatedQuery) return;
    this.flags.set('terminateQueryLoading', true);
    this.apiService.terminateKsqlDbQuery(this.terminatedQuery.id).subscribe({
      next: () => {
        this.queries = this.queries.filter(q => q.id !== this.terminatedQuery.id);
        this.flags.set('terminateQueryLoading', false);
        this.commonService.hideModal('terminateQueryModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('terminateQuery', this.commonService.prepareError(res.error.error,'500','Failed to terminate query!'));
        this.flags.set('terminateQueryLoading', false);
      }
    });
  }

}
