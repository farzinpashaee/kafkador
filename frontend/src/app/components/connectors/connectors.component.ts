import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService, CommonService, ValidationService } from '../../services';
import { GenericResponse, KafkaConnectConfig, ConnectorPlugin, Connector, ConnectorCreateRequest, Error } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';

interface ConfigEntry {
  key: string;
  value: string;
}

@Component({
  selector: 'app-connectors',
  imports: [CommonModule, RouterLink, FormsModule, PaginationComponent],
  templateUrl: './connectors.component.html',
  styleUrl: './connectors.component.scss'
})
export class ConnectorsComponent implements OnInit {

  config: KafkaConnectConfig = { configured: false };
  plugins: ConnectorPlugin[] = [];
  connectors: Connector[] = [];
  selectedConnector: Connector | null = null;

  newConnectorName = '';
  newConnectorPluginClass = '';
  configEntries: ConfigEntry[] = [];

  deletedConnectorName = '';

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  readonly pageSize = 10;
  page = 1;

  get pagedConnectors(): Connector[] {
    return this.connectors.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.getConfig();
  }

  getConfig() {
    this.errors.delete('getConfig');
    this.flags.set('configLoading', true);
    this.apiService.getKafkaConnectConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<KafkaConnectConfig>>) => {
        this.config = res.body?.data ?? { configured: false };
        this.flags.set('configLoading', false);
        if (this.config.configured) this.loadData();
      },
      error: (res: HttpErrorResponse) => {
        if (res.status === 404) {
          this.config = { configured: false };
        } else {
          this.errors.set('getConfig', this.commonService.prepareError(res.error?.error, '500', 'Failed to load Kafka Connect configuration!'));
        }
        this.flags.set('configLoading', false);
      }
    });
  }

  loadData() {
    this.getPlugins();
    this.getConnectors();
  }

  getPlugins() {
    this.errors.delete('getPlugins');
    this.apiService.getConnectorPlugins().subscribe({
      next: (res: HttpResponse<GenericResponse<ConnectorPlugin[]>>) => {
        this.plugins = res.body?.data ?? [];
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getPlugins', this.commonService.prepareError(res.error?.error, '500', 'Failed to load connector plugins!'));
      }
    });
  }

  getConnectors() {
    this.errors.delete('getConnectors');
    this.flags.set('connectorsLoading', true);
    this.apiService.getConnectors().subscribe({
      next: (res: HttpResponse<GenericResponse<Connector[]>>) => {
        this.connectors = res.body?.data ?? [];
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.connectors.length / this.pageSize)));
        this.flags.set('connectorsLoading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getConnectors', this.commonService.prepareError(res.error?.error, '500', 'Failed to load connectors!'));
        this.flags.set('connectorsLoading', false);
      }
    });
  }

  openCreateDialog() {
    this.errors.delete('createConnector');
    this.newConnectorName = '';
    this.newConnectorPluginClass = '';
    this.configEntries = [];
  }

  onPluginChange() {
    const existing = this.configEntries.find(e => e.key === 'connector.class');
    if (existing) {
      existing.value = this.newConnectorPluginClass;
    } else {
      this.configEntries.unshift({ key: 'connector.class', value: this.newConnectorPluginClass });
    }
  }

  addConfigEntry() {
    this.configEntries.push({ key: '', value: '' });
  }

  removeConfigEntry(index: number) {
    this.configEntries.splice(index, 1);
  }

  private buildConfigMap(): { [key: string]: string } {
    const config: { [key: string]: string } = {};
    for (const entry of this.configEntries) {
      if (entry.key.trim()) config[entry.key.trim()] = entry.value;
    }
    return config;
  }

  createConnector() {
    const errors = this.validationService.validateRequiredFields({ name: this.newConnectorName }, ['name']);
    if (errors.length > 0) {
      this.errors.set('createConnector', { code: '400', message: errors[0], datetime: '' });
      return;
    }
    const config = this.buildConfigMap();
    if (!config['connector.class']) {
      this.errors.set('createConnector', { code: '400', message: 'Select a connector plugin.', datetime: '' });
      return;
    }
    this.errors.delete('createConnector');
    this.flags.set('createConnectorLoading', true);
    const request: ConnectorCreateRequest = { name: this.newConnectorName, config };
    this.apiService.createConnector(request).subscribe({
      next: (res: HttpResponse<GenericResponse<Connector>>) => {
        const created = res.body?.data;
        if (created) this.connectors = [...this.connectors, created];
        this.flags.set('createConnectorLoading', false);
        this.commonService.hideModal('createConnectorModal');
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('createConnector', this.commonService.prepareError(res.error?.error, '500', 'Failed to create connector!'));
        this.flags.set('createConnectorLoading', false);
      }
    });
  }

  openEditDialog(connector: Connector) {
    this.errors.delete('editConnector');
    this.selectedConnector = connector;
    this.configEntries = Object.entries(connector.config || {}).map(([key, value]) => ({ key, value }));
  }

  updateConnectorConfig() {
    if (!this.selectedConnector) return;
    const config = this.buildConfigMap();
    this.errors.delete('editConnector');
    this.flags.set('editConnectorLoading', true);
    this.apiService.updateConnectorConfig(this.selectedConnector.name, config).subscribe({
      next: (res: HttpResponse<GenericResponse<Connector>>) => {
        const updated = res.body?.data;
        if (updated) this.connectors = this.connectors.map(c => c.name === updated.name ? updated : c);
        this.flags.set('editConnectorLoading', false);
        this.commonService.hideModal('editConnectorModal');
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('editConnector', this.commonService.prepareError(res.error?.error, '500', 'Failed to update connector configuration!'));
        this.flags.set('editConnectorLoading', false);
      }
    });
  }

  openDetailsDialog(connector: Connector) {
    this.selectedConnector = connector;
  }

  openDeleteDialog(connector: Connector) {
    this.errors.delete('deleteConnector');
    this.deletedConnectorName = connector.name;
  }

  deleteConnector() {
    if (!this.deletedConnectorName) return;
    this.flags.set('deleteConnectorLoading', true);
    this.apiService.deleteConnector(this.deletedConnectorName).subscribe({
      next: () => {
        this.connectors = this.connectors.filter(c => c.name !== this.deletedConnectorName);
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.connectors.length / this.pageSize)));
        this.flags.set('deleteConnectorLoading', false);
        this.commonService.hideModal('deleteConnectorModal');
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('deleteConnector', this.commonService.prepareError(res.error?.error, '500', 'Failed to delete connector!'));
        this.flags.set('deleteConnectorLoading', false);
      }
    });
  }

  pauseConnector(connector: Connector) {
    this.runConnectorAction(connector.name, this.apiService.pauseConnector(connector.name), 'pause');
  }

  resumeConnector(connector: Connector) {
    this.runConnectorAction(connector.name, this.apiService.resumeConnector(connector.name), 'resume');
  }

  restartConnector(connector: Connector) {
    this.runConnectorAction(connector.name, this.apiService.restartConnector(connector.name), 'restart');
  }

  private runConnectorAction(name: string, action$: Observable<HttpResponse<void>>, verb: string) {
    this.errors.delete('connectorAction:' + name);
    this.setConnectorActionLoading(name, true);
    action$.subscribe({
      next: () => {
        // Kafka Connect applies pause/resume/restart asynchronously; give the worker
        // a moment to reflect the new state before re-polling the connector list.
        setTimeout(() => {
          this.apiService.getConnectors().subscribe({
            next: (res: HttpResponse<GenericResponse<Connector[]>>) => {
              this.connectors = res.body?.data ?? this.connectors;
              this.setConnectorActionLoading(name, false);
            },
            error: () => this.setConnectorActionLoading(name, false)
          });
        }, 500);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('connectorAction:' + name, this.commonService.prepareError(res.error?.error, '500', `Failed to ${verb} connector!`));
        this.setConnectorActionLoading(name, false);
      }
    });
  }

  private setConnectorActionLoading(name: string, loading: boolean) {
    this.flags.set('connectorActionLoading:' + name, loading);
  }

  isConnectorActionLoading(name: string): boolean {
    return this.flags.get('connectorActionLoading:' + name) === true;
  }

  connectorActionError(name: string): Error | undefined {
    return this.errors.get('connectorAction:' + name);
  }

  taskSummary(connector: Connector): string {
    const total = connector.tasks?.length ?? 0;
    if (total === 0) return 'No tasks';
    const running = connector.tasks.filter(t => t.state === 'RUNNING').length;
    return `${running}/${total} running`;
  }

  hasFailedTasks(connector: Connector): boolean {
    return !!connector.trace || (connector.tasks ?? []).some(t => !!t.trace);
  }

}
