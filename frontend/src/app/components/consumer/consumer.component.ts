import { Component } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ApiService, CommonService } from '../../services';
import { ConsumerGroupDetails, ConsumerGroupPartition, ConsumerGroupTopic, Error, GenericResponse, OffsetResetRequest,
  OffsetResetResult, OffsetResetTarget } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport } from '../../services/table-export';

export type ConsumerTopicSortKey = 'name' | 'partitionCount' | 'memberCount' | 'lag';

/** One exported row per partition, so the file keeps the per-partition offsets the table expands into. */
interface PartitionExportRow { topic: string; partition: ConsumerGroupPartition; }

const EXPORT_FIELDS: ExportField<PartitionExportRow>[] = [
  { key: 'topic', label: 'Topic', value: r => r.topic },
  { key: 'partition', label: 'Partition', value: r => r.partition.partition },
  { key: 'committedOffset', label: 'Committed offset', value: r => r.partition.committedOffset },
  { key: 'endOffset', label: 'End offset', value: r => r.partition.endOffset },
  { key: 'lag', label: 'Consumer lag', value: r => r.partition.lag },
  { key: 'consumerId', label: 'Consumer ID', value: r => r.partition.consumerId },
  { key: 'clientId', label: 'Client ID', value: r => r.partition.clientId },
  { key: 'host', label: 'Host', value: r => r.partition.host }
];

@Component({
  selector: 'app-consumer',
  imports: [CommonModule, RouterModule, FormsModule, PaginationComponent],
  templateUrl: './consumer.component.html',
  styleUrl: './consumer.component.scss'
})
export class ConsumerComponent {

  groupId = '';
  group?: ConsumerGroupDetails;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  searchTerm = '';
  sortKey: ConsumerTopicSortKey = 'name';
  sortAscending = true;
  expanded = new Set<string>();
  readonly pageSize = 10;
  page = 1;
  readonly exportFormats = EXPORT_FORMATS;

  // Reset offsets dialog. Any change to these clears the preview, so what's applied is what was previewed.
  readonly resetTargets: { value: OffsetResetTarget; label: string }[] = [
    { value: 'EARLIEST', label: 'Earliest' },
    { value: 'LATEST', label: 'Latest' },
    { value: 'TIMESTAMP', label: 'Date & time' },
    { value: 'OFFSET', label: 'Offset' }
  ];
  /** '' resets every topic the group has offsets on. */
  resetTopic = '';
  /** null resets every partition of the topic. */
  resetPartition: number | null = null;
  resetTarget: OffsetResetTarget = 'EARLIEST';
  /** TIMESTAMP, as a datetime-local value in the browser's time zone. */
  resetTime = '';
  resetOffset: number | null = null;
  resetPreview?: OffsetResetResult;
  /** Shown on the page after a reset is applied. */
  resetDone?: string;

  readonly columns: { key: ConsumerTopicSortKey; label: string; hint?: string }[] = [
    { key: 'name', label: 'Topic' },
    { key: 'partitionCount', label: 'Partitions', hint: 'Partitions the group has committed offsets on or is assigned' },
    { key: 'memberCount', label: 'Members', hint: 'Group members currently assigned partitions of this topic' },
    { key: 'lag', label: 'Consumer lag', hint: 'Messages between the committed offset and the end of each partition, summed' }
  ];

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.groupId = this.route.snapshot.paramMap.get('id')!;
    this.loadGroup();
  }

  loadGroup(): void {
    this.flags.set('getGroupLoading', true);
    this.apiService.getConsumerGroupDetails(this.groupId).subscribe({
      next: (res: GenericResponse<ConsumerGroupDetails>) => {
        this.group = res.data;
        this.page = Math.min(this.page, this.pageCount);
        this.errors.delete('getGroup');
        this.flags.set('getGroupLoading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getGroup', this.commonService.prepareError(res.error?.error, '500', 'Failed to get the consumer group!'));
        this.flags.set('getGroupLoading', false);
      }
    });
  }

  /** Topics matching the search box, in the current sort order. */
  get filteredTopics(): ConsumerGroupTopic[] {
    const term = this.searchTerm.trim().toLowerCase();
    const direction = this.sortAscending ? 1 : -1;
    return (this.group?.topics ?? [])
      .filter(t => !term || t.name.toLowerCase().includes(term))
      .sort((a, b) => direction * this.compare(a, b, this.sortKey));
  }

  get pagedTopics(): ConsumerGroupTopic[] {
    return this.filteredTopics.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  private get pageCount(): number {
    return Math.max(1, Math.ceil(this.filteredTopics.length / this.pageSize));
  }

  onSearch(): void {
    this.page = 1;
  }

  sortBy(key: ConsumerTopicSortKey): void {
    this.sortAscending = this.sortKey === key ? !this.sortAscending : true;
    this.sortKey = key;
    this.page = 1;
  }

  ariaSort(key: ConsumerTopicSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) return 'none';
    return this.sortAscending ? 'ascending' : 'descending';
  }

  toggle(topic: ConsumerGroupTopic): void {
    if (this.expanded.has(topic.name)) this.expanded.delete(topic.name);
    else this.expanded.add(topic.name);
  }

  /** Bootstrap badge colour for a group state (Stable, Empty, PreparingRebalance, ...). */
  stateClass(state?: string): string {
    switch ((state ?? '').toLowerCase()) {
      case 'stable': return 'text-bg-success';
      case 'empty': return 'text-bg-secondary';
      case 'dead': return 'text-bg-danger';
      case 'unknown': case '': return 'text-bg-light';
      default: return 'text-bg-warning';   // rebalancing / reconciling / assigning
    }
  }

  formatNumber(value?: number | null): string {
    return value == null ? 'N/A' : value.toLocaleString();
  }

  /** Exports every partition of the topics currently listed (search applied, all pages), in the table's sort order. */
  export(format: ExportFormat): void {
    const rows = this.filteredTopics.flatMap(t => t.partitions.map(partition => ({ topic: t.name, partition })));
    downloadExport(buildExport(format, EXPORT_FIELDS, rows, 'partitions', 'partition'), format, `consumer-group-${this.groupId}`);
  }

  openResetDialog(topic = ''): void {
    this.resetTopic = topic;
    this.resetPartition = null;
    this.onResetChange();
  }

  /** Partitions of the chosen topic that the group knows, to reset just one. */
  get resetPartitions(): number[] {
    const topic = this.group?.topics.find(t => t.name === this.resetTopic);
    return (topic?.partitions ?? []).map(p => p.partition).sort((a, b) => a - b);
  }

  /** What's missing before the reset can be previewed, if anything. */
  get resetInputError(): string | undefined {
    if (this.resetTarget === 'TIMESTAMP' && (!this.resetTime || isNaN(new Date(this.resetTime).getTime()))) {
      return 'Choose the date and time to reset to.';
    }
    if (this.resetTarget === 'OFFSET' && (this.resetOffset == null || this.resetOffset < 0 || !Number.isInteger(this.resetOffset))) {
      return 'Enter the offset to reset to, 0 or more.';
    }
    return undefined;
  }

  get canApplyReset(): boolean {
    return !!this.resetPreview && !this.resetPreview.blockedReason && this.resetPreview.changes.length > 0
      && !this.flags.get('resetLoading');
  }

  onResetChange(): void {
    this.resetPreview = undefined;
    this.errors.delete('reset');
  }

  onResetTopicChange(): void {
    this.resetPartition = null;
    this.onResetChange();
  }

  previewReset(): void {
    this.sendReset(true);
  }

  applyReset(): void {
    if (this.canApplyReset) this.sendReset(false);
  }

  buildResetRequest(dryRun: boolean): OffsetResetRequest {
    return {
      topic: this.resetTopic || undefined,
      partitions: this.resetTopic && this.resetPartition != null ? [this.resetPartition] : [],
      target: this.resetTarget,
      timestamp: this.resetTarget === 'TIMESTAMP' ? new Date(this.resetTime).getTime() : undefined,
      offset: this.resetTarget === 'OFFSET' ? this.resetOffset ?? undefined : undefined,
      dryRun
    };
  }

  private sendReset(dryRun: boolean): void {
    if (this.resetInputError) return;
    this.flags.set('resetLoading', true);
    this.errors.delete('reset');
    this.apiService.resetConsumerGroupOffsets(this.groupId, this.buildResetRequest(dryRun)).subscribe({
      next: (res: GenericResponse<OffsetResetResult>) => {
        this.flags.set('resetLoading', false);
        if (dryRun) {
          this.resetPreview = res.data;
          return;
        }
        const count = res.data?.changes.length ?? 0;
        this.resetDone = `Reset ${count} ${count === 1 ? 'partition' : 'partitions'} to ${this.resetTargetLabel.toLowerCase()}.`;
        this.resetPreview = undefined;
        this.commonService.hideModal('resetOffsetsModal');
        this.loadGroup();
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('reset', this.commonService.prepareError(res.error?.error, '500', 'Failed to reset the offsets!'));
        this.flags.set('resetLoading', false);
      }
    });
  }

  get resetTargetLabel(): string {
    return this.resetTargets.find(t => t.value === this.resetTarget)?.label ?? this.resetTarget;
  }

  private compare(a: ConsumerGroupTopic, b: ConsumerGroupTopic, key: ConsumerTopicSortKey): number {
    if (key === 'name') return a.name.localeCompare(b.name);
    // Unknown values (N/A) sort last in ascending order.
    const x = a[key] ?? Number.POSITIVE_INFINITY;
    const y = b[key] ?? Number.POSITIVE_INFINITY;
    return x === y ? 0 : (x < y ? -1 : 1);
  }

}
