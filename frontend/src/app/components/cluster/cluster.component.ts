import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService, LocalStorageService } from '../../services';
import { Error, Connection, GenericResponse, ClusterOverview, BrokerOverview } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport, formatBytes } from '../../services/table-export';

export type BrokerSortKey = 'id' | 'diskUsageBytes' | 'inSyncReplicaCount' | 'replicaCount' | 'replicasSkew'
  | 'leaderCount' | 'leadersSkew' | 'port' | 'host';

const EXPORT_FIELDS: ExportField<BrokerOverview>[] = [
  { key: 'brokerId', label: 'Broker ID', value: b => b.id },
  { key: 'activeController', label: 'Active controller', value: b => b.activeController },
  { key: 'diskUsageBytes', label: 'Disk usage (bytes)', value: b => b.diskUsageBytes },
  { key: 'logs', label: 'Logs', value: b => b.logCount },
  { key: 'inSyncReplicas', label: 'In sync replicas', value: b => b.inSyncReplicaCount },
  { key: 'replicas', label: 'Replicas', value: b => b.replicaCount },
  { key: 'replicasSkewPercent', label: 'Replicas skew (%)', value: b => b.replicasSkew == null ? undefined : Number(b.replicasSkew.toFixed(2)) },
  { key: 'leaders', label: 'Leaders', value: b => b.leaderCount },
  { key: 'leadersSkewPercent', label: 'Leaders skew (%)', value: b => b.leadersSkew == null ? undefined : Number(b.leadersSkew.toFixed(2)) },
  { key: 'port', label: 'Port', value: b => b.port },
  { key: 'host', label: 'Host', value: b => b.host },
  { key: 'rack', label: 'Rack', value: b => b.rack }
];

@Component({
  selector: 'app-cluster',
  imports: [CommonModule,FormsModule,RouterModule,PaginationComponent],
  templateUrl: './cluster.component.html',
  styleUrl: './cluster.component.scss'
})
export class ClusterComponent{

  overview?: ClusterOverview;
  activeConnection: Connection | null = null;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  readonly pageSize = 10;
  page = 1;
  sortKey: BrokerSortKey = 'id';
  sortAscending = true;
  searchTerm = '';
  readonly exportFormats = EXPORT_FORMATS;

  readonly columns: { key: BrokerSortKey; label: string; hint?: string }[] = [
    { key: 'id', label: 'Broker ID' },
    { key: 'diskUsageBytes', label: 'Disk usage', hint: 'Size of all partition logs on the broker, and how many logs that is' },
    { key: 'inSyncReplicaCount', label: 'In sync replicas' },
    { key: 'replicaCount', label: 'Replicas' },
    { key: 'replicasSkew', label: 'Replicas skew', hint: 'How far the broker\'s replica count is above (+) or below (-) an even share across brokers' },
    { key: 'leaderCount', label: 'Leaders' },
    { key: 'leadersSkew', label: 'Leaders skew', hint: 'How far the broker\'s partition leader count is above (+) or below (-) an even share across brokers' },
    { key: 'port', label: 'Port' },
    { key: 'host', label: 'Host' }
  ];

  /** Brokers matching the search box, in the current sort order; what the table pages through and exports. */
  get sortedBrokers(): BrokerOverview[] {
    const term = this.searchTerm.trim().toLowerCase();
    const brokers = (this.overview?.brokers ?? []).filter(b => !term || this.searchableText(b).includes(term));
    const direction = this.sortAscending ? 1 : -1;
    return brokers.sort((a, b) => direction * this.compare(a, b, this.sortKey));
  }

  get pagedBrokers(): BrokerOverview[] {
    return this.sortedBrokers.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private commonService: CommonService,
    private localStorageService: LocalStorageService,
    private apiService: ApiService) {}

  ngOnInit() {
    this.activeConnection = this.localStorageService.getItem<Connection>("activeConnection");
    this.flags.set('getOverviewLoading',true);
    this.apiService.getClusterOverview().subscribe({
      next: (res: GenericResponse<ClusterOverview>) => {
        this.overview = res.data;
        this.flags.set('getOverviewLoading',false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getOverview', this.commonService.prepareError(res.error?.error, '500', 'Failed to get the broker and partition overview!'));
        this.flags.set('getOverviewLoading',false);
      }
    });
  }

  sortBy(key: BrokerSortKey): void {
    this.sortAscending = this.sortKey === key ? !this.sortAscending : true;
    this.sortKey = key;
    this.page = 1;
  }

  onSearch(): void {
    this.page = 1;
  }

  ariaSort(key: BrokerSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) return 'none';
    return this.sortAscending ? 'ascending' : 'descending';
  }

  formatDiskUsage(broker: BrokerOverview): string {
    if (broker.diskUsageBytes == null) return 'n/a';
    const logs = broker.logCount ?? 0;
    return `${this.formatBytes(broker.diskUsageBytes)}, ${logs} log${logs === 1 ? '' : 's'}`;
  }

  formatBytes(bytes: number): string {
    return formatBytes(bytes);
  }

  /** "-" when there is nothing to compare or the broker holds exactly its even share. */
  formatSkew(skew?: number): string {
    if (skew == null || Math.abs(skew) < 0.005) return '-';
    return `${skew > 0 ? '+' : ''}${skew.toFixed(2)}%`;
  }

  /** Exports the brokers currently listed (search applied, all pages) in the table's sort order. */
  export(format: ExportFormat): void {
    downloadExport(this.exportContent(format, this.sortedBrokers), format, `brokers-${this.activeConnection?.name ?? 'cluster'}`);
  }

  exportContent(format: ExportFormat, brokers: BrokerOverview[]): string {
    return buildExport(format, EXPORT_FIELDS, brokers, 'brokers', 'broker');
  }

  /** Everything the row shows, so a search for e.g. "kafka2", "29092" or "controller" finds it. */
  private searchableText(b: BrokerOverview): string {
    return [b.id, b.host, b.port, b.rack, b.activeController ? 'controller' : '', this.formatDiskUsage(b),
      b.inSyncReplicaCount, b.replicaCount, this.formatSkew(b.replicasSkew), b.leaderCount, this.formatSkew(b.leadersSkew)]
      .join(' ').toLowerCase();
  }

  private compare(a: BrokerOverview, b: BrokerOverview, key: BrokerSortKey): number {
    if (key === 'id') return Number(a.id) - Number(b.id) || a.id.localeCompare(b.id);
    if (key === 'host') return a.host.localeCompare(b.host);
    // Unknown values (e.g. no disk usage data) sort last in ascending order.
    const x = a[key] ?? Number.POSITIVE_INFINITY;
    const y = b[key] ?? Number.POSITIVE_INFINITY;
    return x === y ? 0 : (x < y ? -1 : 1);
  }

}
