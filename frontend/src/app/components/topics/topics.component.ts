import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { ApiService, CommonService, LocalStorageService, ValidationService } from '../../services';
import { GenericResponse, Topic, TopicOverview, Chart, Error } from '../../models';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport, formatBytes } from '../../services/table-export';

export type TopicSortKey = 'name' | 'partitionCount' | 'outOfSyncReplicaCount' | 'replicationFactor' | 'messageCount' | 'sizeBytes';

const SHOW_INTERNAL_STORAGE_KEY = 'topics.showInternal';

const EXPORT_FIELDS: ExportField<TopicOverview>[] = [
  { key: 'name', label: 'Topic name', value: t => t.name },
  { key: 'id', label: 'Topic ID', value: t => t.id },
  { key: 'internal', label: 'Internal', value: t => t.internal },
  { key: 'partitions', label: 'Partitions', value: t => t.partitionCount },
  { key: 'outOfSyncReplicas', label: 'Out of sync replicas', value: t => t.outOfSyncReplicaCount },
  { key: 'replicationFactor', label: 'Replication factor', value: t => t.replicationFactor },
  { key: 'messages', label: 'Number of messages', value: t => t.messageCount },
  { key: 'sizeBytes', label: 'Size (bytes)', value: t => t.sizeBytes },
  { key: 'cleanupPolicy', label: 'Cleanup policy', value: t => t.cleanupPolicy }
];

@Component({
  selector: 'app-topics',
  imports: [CommonModule,RouterModule,NgxChartsModule,FormsModule,ReactiveFormsModule,PaginationComponent],
  templateUrl: './topics.component.html',
  styleUrl: './topics.component.scss'
})
export class TopicsComponent {

  data = [
    { name: 'a1', value: 5000 },
    { name: 'a2', value: 3000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 3450 },
    { name: 'a5', value: 2400 }
  ];

  cartTopWidgetWhiteScheme: Color = {
    name: 'cartTopWidgetWhiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };

  topics: TopicOverview[] = [];
  newTopic!: Topic;
  deletedTopic!: TopicOverview;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  filter = new FormControl('', { nonNullable: true });
  searchTerm = '';
  showInternal = true;
  sortKey: TopicSortKey = 'name';
  sortAscending = true;
  readonly pageSize = 10;
  page = 1;
  readonly exportFormats = EXPORT_FORMATS;

  readonly columns: { key: TopicSortKey; label: string; hint?: string }[] = [
    { key: 'name', label: 'Topic name' },
    { key: 'partitionCount', label: 'Partitions' },
    { key: 'outOfSyncReplicaCount', label: 'Out of sync replicas' },
    { key: 'replicationFactor', label: 'Replication factor' },
    { key: 'messageCount', label: 'Number of messages', hint: 'End minus start offset over all partitions. N/A for compacted topics, where that isn\'t a message count' },
    { key: 'sizeBytes', label: 'Size', hint: 'Disk space used by the topic across all brokers, every replica counted' }
  ];

  /** Topics matching the search box and the internal-topics filter, in the current sort order. */
  get filteredTopics(): TopicOverview[] {
    const term = this.searchTerm.trim().toLowerCase();
    const direction = this.sortAscending ? 1 : -1;
    return this.topics
      .filter(t => (this.showInternal || !t.internal) && (!term || t.name.toLowerCase().includes(term)))
      .sort((a, b) => direction * this.compare(a, b, this.sortKey));
  }

  get pagedTopics(): TopicOverview[] {
    return this.filteredTopics.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  get internalTopicCount(): number {
    return this.topics.filter(t => t.internal).length;
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService,
    private localStorageService: LocalStorageService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.newTopic = this.blankTopic();
    this.deletedTopic = this.blankOverview();
    this.showInternal = this.localStorageService.getItem<boolean>(SHOW_INTERNAL_STORAGE_KEY) ?? true;
    this.flags.set('getTopicLoading',true);
    this.flags.set('agentEnabled',true);

    this.filter.valueChanges.pipe(debounceTime(200), distinctUntilChanged()).subscribe(text => {
      this.searchTerm = text;
      this.page = 1;
    });

    this.loadTopics();

    this.apiService.getChart('x','q').subscribe({ next: (res: HttpResponse<GenericResponse<Chart>>) => {
      // TODO: get chart data
      },
      error: (res:HttpErrorResponse) => {
        if(res.status==428){
          this.flags.set('agentEnabled',false);
        } else {
          this.errors.set("getChart",this.commonService.prepareError(res.error.error,'500','Failed to get cluster chart information!'));
          this.flags.set('getChartLoading',false);
        }
      }
    });
  }

  loadTopics(): void {
    this.apiService.getTopicsOverview().subscribe({ next: (res: GenericResponse<TopicOverview[]>) => {
        this.topics = res.data ?? [];
        this.page = 1;
        this.errors.delete('getTopics');
        this.flags.set('getTopicLoading',false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("getTopics",this.commonService.prepareError(res.error?.error,'500','Failed to get topics!'));
        this.flags.set('getTopicLoading',false);
      }
    });
  }

  setShowInternal(show: boolean): void {
    this.showInternal = show;
    this.page = 1;
    this.localStorageService.setItem(SHOW_INTERNAL_STORAGE_KEY, show);
  }

  sortBy(key: TopicSortKey): void {
    this.sortAscending = this.sortKey === key ? !this.sortAscending : true;
    this.sortKey = key;
    this.page = 1;
  }

  ariaSort(key: TopicSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) return 'none';
    return this.sortAscending ? 'ascending' : 'descending';
  }

  formatSize(bytes?: number): string {
    return bytes == null ? 'N/A' : formatBytes(bytes);
  }

  /** Exports the topics currently listed (search and filter applied, all pages) in the table's sort order. */
  export(format: ExportFormat): void {
    const connection = this.localStorageService.getItem<{ name?: string }>('activeConnection');
    downloadExport(this.exportContent(format, this.filteredTopics), format, `topics-${connection?.name ?? 'cluster'}`);
  }

  exportContent(format: ExportFormat, topics: TopicOverview[]): string {
    return buildExport(format, EXPORT_FIELDS, topics, 'topics', 'topic');
  }

  createTopic(){
    const errors = [
      ...this.validationService.validateRequiredFields(this.newTopic, ['name', 'partitions', 'replicatorFactor']),
      ...this.validationService.validatePositiveIntegerFields(this.newTopic, ['partitions', 'replicatorFactor'])
    ];
    if (errors.length > 0) {
      this.errors.set("createTopic",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('createTopic');
    }
    this.flags.set('createTopicLoading',true);
    this.apiService.createTopic(this.newTopic).subscribe({
      next: () => {
        this.newTopic = this.blankTopic();
        this.flags.set('createTopicLoading',false);
        this.commonService.hideModal('createTopicModal');
        this.loadTopics();
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("createTopic",this.commonService.prepareError(res.error.error,'500','Failed to add new Topic!'));
        this.flags.set('createTopicLoading',false);
      }
    });
  }

  openDeleteDialog(topic: TopicOverview) {
    this.errors.delete('deleteTopic');
    this.deletedTopic = topic;
  }

  deleteTopic(){
    if (!this.deletedTopic) return;
    this.flags.set('deleteTopicLoading',true);
    this.apiService.deleteTopic(this.deletedTopic.name).subscribe({
        next: () => {
          this.topics = this.topics.filter(c => c.name !== this.deletedTopic.name);
          this.page = Math.min(this.page, Math.max(1, Math.ceil(this.filteredTopics.length / this.pageSize)));
          this.flags.set('deleteTopicLoading',false);
          this.commonService.hideModal('deleteTopicModal');
        },
        error: (res:HttpErrorResponse) => {
          this.errors.set("deleteTopic",this.commonService.prepareError(res.error.error,'500','Failed to delete topic!'));
          this.flags.set('deleteTopicLoading',false);
        }
      });
  }

  private compare(a: TopicOverview, b: TopicOverview, key: TopicSortKey): number {
    if (key === 'name') return a.name.localeCompare(b.name);
    // Unknown values (N/A) sort last in ascending order.
    const x = a[key] ?? Number.POSITIVE_INFINITY;
    const y = b[key] ?? Number.POSITIVE_INFINITY;
    return x === y ? 0 : (x < y ? -1 : 1);
  }

  private blankTopic(): Topic {
    return { name: '' , id : '' , partitions : 1 , internal: false , replicatorFactor: 1, config: [] };
  }

  private blankOverview(): TopicOverview {
    return { name: '', internal: false, partitionCount: 0, replicationFactor: 0, outOfSyncReplicaCount: 0 };
  }

}
