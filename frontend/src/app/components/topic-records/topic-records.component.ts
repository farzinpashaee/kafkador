import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ApiService, CommonService } from '../../services';
import { Error, GenericResponse, RecordFilter, RecordSeekType, RecordSerde, TopicRecord, TopicRecordProduceResult,
  TopicRecordSearchRequest, TopicRecordSearchResult } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { CodeEditorComponent } from '../code-editor/code-editor.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport, formatBytes } from '../../services/table-export';

export type RecordSortKey = 'offset' | 'partition' | 'timestamp';

/** Live mode keeps this many of the newest records; older ones drop off the end of the table. */
export const MAX_LIVE_RECORDS = 500;

const EXPORT_FIELDS: ExportField<TopicRecord>[] = [
  { key: 'partition', label: 'Partition', value: r => r.partition },
  { key: 'offset', label: 'Offset', value: r => r.offset },
  { key: 'timestamp', label: 'Timestamp', value: r => new Date(r.timestamp).toISOString() },
  { key: 'key', label: 'Key', value: r => r.key },
  { key: 'value', label: 'Value', value: r => r.value },
  { key: 'headers', label: 'Headers', value: r => r.headers.length ? JSON.stringify(r.headers) : null }
];

/** Records tab of the topic page: reads a window of records with server-side seek, serdes and filters. */
@Component({
  selector: 'app-topic-records',
  imports: [CommonModule, FormsModule, PaginationComponent, CodeEditorComponent],
  templateUrl: './topic-records.component.html',
  styleUrl: './topic-records.component.scss'
})
export class TopicRecordsComponent implements OnInit, OnDestroy {

  @Input({ required: true }) topicName!: string;
  @Input() partitionCount = 0;

  readonly seekTypes: { value: RecordSeekType; label: string }[] = [
    { value: 'NEWEST', label: 'Newest' },
    { value: 'OLDEST', label: 'Oldest' },
    { value: 'OFFSET', label: 'From offset' },
    { value: 'TIMESTAMP', label: 'From time' }
  ];
  readonly serdes: { value: RecordSerde; label: string }[] = [
    { value: 'STRING', label: 'String' },
    { value: 'JSON', label: 'JSON' },
    { value: 'INT', label: 'Int' },
    { value: 'LONG', label: 'Long' },
    { value: 'FLOAT', label: 'Float' },
    { value: 'DOUBLE', label: 'Double' },
    { value: 'BASE64', label: 'Bytes (Base64)' },
    { value: 'HEX', label: 'Bytes (Hex)' }
  ];
  readonly filterFields: { value: RecordFilter['field']; label: string }[] = [
    { value: 'ANY', label: 'Key, value or headers' },
    { value: 'KEY', label: 'Key' },
    { value: 'VALUE', label: 'Value' },
    { value: 'HEADERS', label: 'Headers' }
  ];
  readonly filterOperators: { value: RecordFilter['operator']; label: string }[] = [
    { value: 'CONTAINS', label: 'contains' },
    { value: 'NOT_CONTAINS', label: 'does not contain' },
    { value: 'EQUALS', label: 'equals' }
  ];
  readonly limits = [50, 100, 250, 500];
  readonly exportFormats = EXPORT_FORMATS;
  readonly columns: { key: RecordSortKey; label: string }[] = [
    { key: 'offset', label: 'Offset' },
    { key: 'partition', label: 'Partition' },
    { key: 'timestamp', label: 'Timestamp' }
  ];

  seekType: RecordSeekType = 'NEWEST';
  offset: number | null = null;
  /** datetime-local value, in the browser's time zone. */
  fromTime = '';
  selectedPartitions = new Set<number>();
  keySerde: RecordSerde = 'STRING';
  valueSerde: RecordSerde = 'STRING';
  limit = 100;
  filters: RecordFilter[] = [];

  result?: TopicRecordSearchResult;
  errors: Map<string, Error> = new Map();
  loading = false;
  expanded = new Set<string>();
  sortKey?: RecordSortKey;
  sortAscending = true;
  readonly pageSize = 20;
  page = 1;
  private request?: Subscription;
  /** Live mode: streaming records produced from the moment it started. */
  live = false;
  liveConnecting = false;
  liveReceived = 0;
  private liveSubscription?: Subscription;
  /** Produce dialog. Null partition lets the producer choose (by key hash). */
  producePartition: number | null = null;
  produceKeySerde: RecordSerde = 'STRING';
  produceValueSerde: RecordSerde = 'STRING';
  produceKey = '';
  produceValue = '';
  produceHeaders = '{}';
  keepProduceContents = false;
  producing = false;
  produceResult?: TopicRecordProduceResult;

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
    this.stopLive();
  }

  get partitions(): number[] {
    return Array.from({ length: this.partitionCount }, (_, i) => i);
  }

  get partitionLabel(): string {
    const selected = [...this.selectedPartitions].sort((a, b) => a - b);
    if (!selected.length) return 'All partitions';
    return selected.length <= 3 ? `Partition${selected.length > 1 ? 's' : ''} ${selected.join(', ')}` : `${selected.length} partitions`;
  }

  /** Loaded records in the server's order (newest or oldest first) unless a column sort is picked. */
  get sortedRecords(): TopicRecord[] {
    const records = this.result?.records ?? [];
    if (!this.sortKey) return records;
    const key = this.sortKey;
    const direction = this.sortAscending ? 1 : -1;
    return [...records].sort((a, b) => direction * (a[key] - b[key] || a.partition - b.partition || a.offset - b.offset));
  }

  get pagedRecords(): TopicRecord[] {
    return this.sortedRecords.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  get filtersReady(): boolean {
    return this.filters.every(f => f.value.trim());
  }

  /** Every filter needs its text, and OFFSET / TIMESTAMP their start value, before anything can be read. */
  get canLoad(): boolean {
    if (this.live || !this.filtersReady) return false;
    if (this.seekType === 'OFFSET') return this.offset != null && this.offset >= 0;
    if (this.seekType === 'TIMESTAMP') return !!this.fromTime && !isNaN(new Date(this.fromTime).getTime());
    return true;
  }

  load(): void {
    if (!this.canLoad) return;
    this.request?.unsubscribe();
    this.loading = true;
    this.errors.delete('records');
    this.request = this.apiService.searchTopicRecords(this.topicName, this.buildRequest()).subscribe({
      next: (res: GenericResponse<TopicRecordSearchResult>) => {
        this.result = res.data;
        this.expanded.clear();
        this.page = 1;
        this.loading = false;
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('records', this.commonService.prepareError(res.error?.error, '500', 'Failed to read records!'));
        this.loading = false;
      }
    });
  }

  toggleLive(): void {
    if (this.live) this.stopLive(); else this.startLive();
  }

  /**
   * Clears the table and streams records produced from now on into its top, newest first, using the selected
   * partitions, serdes and filters. Those controls are locked while live; Stop, then change them and start again.
   */
  startLive(): void {
    if (this.live || !this.filtersReady) return;
    this.request?.unsubscribe();
    this.loading = false;
    this.errors.delete('records');
    this.live = true;
    this.liveConnecting = true;
    this.liveReceived = 0;
    this.result = { records: [], scannedCount: 0, scanLimitReached: false };
    this.sortKey = undefined;
    this.expanded.clear();
    this.page = 1;
    this.liveSubscription = this.apiService.listenTopicRecords(this.topicName, {
      partitions: [...this.selectedPartitions].sort((a, b) => a - b),
      keySerde: this.keySerde,
      valueSerde: this.valueSerde,
      filters: this.filters.map(f => ({ ...f, value: f.value.trim() }))
    }).subscribe({
      next: batch => {
        this.liveConnecting = false;
        if (!batch.length || !this.result) return;
        this.liveReceived += batch.length;
        const newestFirst = [...batch].sort((a, b) => b.timestamp - a.timestamp || b.offset - a.offset);
        this.result = { ...this.result, records: [...newestFirst, ...this.result.records].slice(0, MAX_LIVE_RECORDS) };
      },
      error: (error: Error) => {
        this.errors.set('records', { code: '500', message: error.message, datetime: '' });
        this.live = false;
        this.liveConnecting = false;
      }
    });
  }

  /** Closes the stream; the records received so far stay in the table. */
  stopLive(): void {
    this.liveSubscription?.unsubscribe();
    this.liveSubscription = undefined;
    this.live = false;
    this.liveConnecting = false;
  }

  /** Seek type changes reload straight away, except the ones still waiting for their start value. */
  onSeekTypeChange(): void {
    if (this.seekType === 'NEWEST' || this.seekType === 'OLDEST') this.load();
  }

  togglePartition(partition: number, selected: boolean): void {
    if (selected) this.selectedPartitions.add(partition); else this.selectedPartitions.delete(partition);
    this.load();
  }

  selectAllPartitions(): void {
    this.selectedPartitions.clear();
    this.load();
  }

  addFilter(): void {
    this.filters.push({ field: 'ANY', operator: 'CONTAINS', value: '' });
  }

  removeFilter(index: number): void {
    const hadValue = !!this.filters[index].value.trim();
    this.filters.splice(index, 1);
    if (hadValue) this.load();
  }

  sortBy(key: RecordSortKey): void {
    this.sortAscending = this.sortKey === key ? !this.sortAscending : true;
    this.sortKey = key;
    this.page = 1;
  }

  ariaSort(key: RecordSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey !== key) return 'none';
    return this.sortAscending ? 'ascending' : 'descending';
  }

  rowId(record: TopicRecord): string {
    return `${record.partition}-${record.offset}`;
  }

  toggle(record: TopicRecord): void {
    const id = this.rowId(record);
    if (this.expanded.has(id)) this.expanded.delete(id); else this.expanded.add(id);
  }

  /** e.g. 2026-10-11 17:17:19.207, in the browser's time zone. */
  formatTimestamp(timestamp: number): string {
    if (timestamp < 0) return 'N/A';
    const d = new Date(timestamp);
    const pad = (n: number, width = 2) => String(n).padStart(width, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
      + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.${pad(d.getMilliseconds(), 3)}`;
  }

  formatSize(size: number): string {
    return size < 0 ? 'null' : formatBytes(size);
  }

  /** The full key/value for the expanded row: JSON is pretty-printed, anything else shown as is. */
  formatBody(text?: string | null): string {
    if (text == null) return 'null';
    const trimmed = text.trim();
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      try {
        return JSON.stringify(JSON.parse(trimmed), null, 2);
      } catch {
        // not JSON after all
      }
    }
    return text;
  }

  /** Opens the dialog with the table's serdes, unless the last contents were kept. */
  openProduceDialog(): void {
    this.errors.delete('produce');
    this.produceResult = undefined;
    if (!this.keepProduceContents) {
      this.produceKeySerde = this.keySerde;
      this.produceValueSerde = this.valueSerde;
    }
  }

  /** The headers editor as a name to value map, or an error message when it isn't a JSON object of plain values. */
  get parsedHeaders(): { headers?: Record<string, string | null>; error?: string } {
    const text = this.produceHeaders.trim();
    if (!text) return { headers: {} };
    let parsed: unknown;
    try {
      parsed = JSON.parse(text);
    } catch {
      return { error: 'Headers must be valid JSON, e.g. {"trace-id": "abc"}' };
    }
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { error: 'Headers must be a JSON object of names to values' };
    }
    const headers: Record<string, string | null> = {};
    for (const [name, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (value !== null && typeof value === 'object') return { error: `Header "${name}" must be a string, number or boolean` };
      headers[name] = value === null ? null : String(value);
    }
    return { headers };
  }

  /** A JSON key or value has to parse; the server checks the other serdes. */
  get produceValueError(): string | undefined {
    return this.jsonError(this.produceValueSerde, this.produceValue);
  }

  get produceKeyError(): string | undefined {
    return this.jsonError(this.produceKeySerde, this.produceKey);
  }

  get canProduce(): boolean {
    return !this.producing && !this.parsedHeaders.error && !this.produceValueError && !this.produceKeyError;
  }

  /** Sends one record; an empty key / value is sent as null. Reloads the table so the record shows up. */
  produce(): void {
    const { headers } = this.parsedHeaders;
    if (!this.canProduce || !headers) return;
    this.producing = true;
    this.errors.delete('produce');
    this.produceResult = undefined;
    this.apiService.produceTopicRecord(this.topicName, {
      partition: this.producePartition,
      key: this.produceKey === '' ? null : this.produceKey,
      value: this.produceValue === '' ? null : this.produceValue,
      keySerde: this.produceKeySerde,
      valueSerde: this.produceValueSerde,
      headers: headers as Record<string, string>
    }).subscribe({
      next: (res: GenericResponse<TopicRecordProduceResult>) => {
        this.producing = false;
        this.load();
        if (this.keepProduceContents) {
          this.produceResult = res.data;
        } else {
          this.produceKey = '';
          this.produceValue = '';
          this.produceHeaders = '{}';
          this.commonService.hideModal('produceRecordModal');
        }
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('produce', this.commonService.prepareError(res.error?.error, '500', 'Failed to produce the record!'));
        this.producing = false;
      }
    });
  }

  private jsonError(serde: RecordSerde, text: string): string | undefined {
    if (serde !== 'JSON' || !text.trim()) return undefined;
    try {
      JSON.parse(text);
      return undefined;
    } catch {
      return 'Not valid JSON';
    }
  }

  /** Exports the loaded records in the table's current order. */
  export(format: ExportFormat): void {
    downloadExport(buildExport(format, EXPORT_FIELDS, this.sortedRecords, 'records', 'record'), format,
      `${this.topicName}-records`);
  }

  private buildRequest(): TopicRecordSearchRequest {
    return {
      seekType: this.seekType,
      partitions: [...this.selectedPartitions].sort((a, b) => a - b),
      offset: this.seekType === 'OFFSET' ? this.offset : null,
      timestamp: this.seekType === 'TIMESTAMP' ? new Date(this.fromTime).getTime() : null,
      limit: this.limit,
      keySerde: this.keySerde,
      valueSerde: this.valueSerde,
      filters: this.filters.map(f => ({ ...f, value: f.value.trim() }))
    };
  }

}
