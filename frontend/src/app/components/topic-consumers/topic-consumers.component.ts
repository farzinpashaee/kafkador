import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { Error, GenericResponse, TopicConsumerGroup } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport } from '../../services/table-export';

export type TopicConsumerSortKey = 'groupId' | 'state' | 'memberCount' | 'partitionCount' | 'lag';

const EXPORT_FIELDS: ExportField<TopicConsumerGroup>[] = [
  { key: 'groupId', label: 'Consumer group', value: g => g.groupId },
  { key: 'state', label: 'State', value: g => g.state },
  { key: 'members', label: 'Members on this topic', value: g => g.memberCount },
  { key: 'groupMembers', label: 'Members in group', value: g => g.groupMemberCount },
  { key: 'partitions', label: 'Partitions', value: g => g.partitionCount },
  { key: 'lag', label: 'Consumer lag', value: g => g.lag },
  { key: 'coordinatorId', label: 'Coordinator ID', value: g => g.coordinatorId }
];

/** Consumers tab of the topic page: the consumer groups reading this topic, with their lag on it. */
@Component({
  selector: 'app-topic-consumers',
  imports: [CommonModule, FormsModule, RouterModule, PaginationComponent],
  templateUrl: './topic-consumers.component.html',
  styleUrl: './topic-consumers.component.scss'
})
export class TopicConsumersComponent implements OnInit {

  @Input({ required: true }) topicName!: string;

  groups: TopicConsumerGroup[] = [];
  errors: Map<string, Error> = new Map();
  loading = false;
  loaded = false;
  searchTerm = '';
  sortKey: TopicConsumerSortKey = 'groupId';
  sortAscending = true;
  readonly pageSize = 10;
  page = 1;
  readonly exportFormats = EXPORT_FORMATS;

  readonly columns: { key: TopicConsumerSortKey; label: string; hint?: string }[] = [
    { key: 'groupId', label: 'Consumer group' },
    { key: 'state', label: 'State' },
    { key: 'memberCount', label: 'Members', hint: 'Members currently assigned partitions of this topic, of all members in the group' },
    { key: 'partitionCount', label: 'Partitions', hint: 'Partitions of this topic the group has committed offsets on or is assigned' },
    { key: 'lag', label: 'Consumer lag', hint: 'Messages between the group\'s committed offsets and the end of the topic, summed over partitions' }
  ];

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.apiService.getTopicConsumerGroups(this.topicName).subscribe({
      next: (res: GenericResponse<TopicConsumerGroup[]>) => {
        this.groups = res.data ?? [];
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.filteredGroups.length / this.pageSize)));
        this.errors.delete('groups');
        this.loading = false;
        this.loaded = true;
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('groups', this.commonService.prepareError(res.error?.error, '500', 'Failed to get the consumer groups of this topic!'));
        this.loading = false;
        this.loaded = true;
      }
    });
  }

  /** Groups matching the search box, in the current sort order. */
  get filteredGroups(): TopicConsumerGroup[] {
    const term = this.searchTerm.trim().toLowerCase();
    const direction = this.sortAscending ? 1 : -1;
    return this.groups
      .filter(g => !term || g.groupId.toLowerCase().includes(term))
      .sort((a, b) => direction * this.compare(a, b, this.sortKey));
  }

  get pagedGroups(): TopicConsumerGroup[] {
    return this.filteredGroups.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  get totalLag(): number | undefined {
    const known = this.groups.map(g => g.lag).filter((lag): lag is number => lag != null);
    return known.length ? known.reduce((sum, lag) => sum + lag, 0) : undefined;
  }

  get activeGroupCount(): number {
    return this.groups.filter(g => g.memberCount > 0).length;
  }

  onSearch(): void {
    this.page = 1;
  }

  sortBy(key: TopicConsumerSortKey): void {
    this.sortAscending = this.sortKey === key ? !this.sortAscending : true;
    this.sortKey = key;
    this.page = 1;
  }

  ariaSort(key: TopicConsumerSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) return 'none';
    return this.sortAscending ? 'ascending' : 'descending';
  }

  /** Bootstrap badge colour for a group state (Stable, Empty, PreparingRebalance, ...). */
  stateClass(state?: string): string {
    switch ((state ?? '').toLowerCase()) {
      case 'stable': return 'text-bg-success';
      case 'empty': return 'text-bg-secondary';
      case 'dead': return 'text-bg-danger';
      case 'unknown': case '': return 'text-bg-light';
      default: return 'text-bg-warning';
    }
  }

  formatNumber(value?: number | null): string {
    return value == null ? 'N/A' : value.toLocaleString();
  }

  /** Exports the groups currently listed (search applied, all pages) in the table's sort order. */
  export(format: ExportFormat): void {
    downloadExport(buildExport(format, EXPORT_FIELDS, this.filteredGroups, 'consumerGroups', 'consumerGroup'), format,
      `${this.topicName}-consumer-groups`);
  }

  private compare(a: TopicConsumerGroup, b: TopicConsumerGroup, key: TopicConsumerSortKey): number {
    if (key === 'groupId' || key === 'state') return (a[key] ?? '').localeCompare(b[key] ?? '');
    // Unknown values (N/A) sort last in ascending order.
    const x = a[key] ?? Number.POSITIVE_INFINITY;
    const y = b[key] ?? Number.POSITIVE_INFINITY;
    return x === y ? 0 : (x < y ? -1 : 1);
  }

}
