import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService, CommonService } from '../../services';
import { EntityMetrics, Error, GenericResponse, MetricSeries, MetricSource } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';

export type MetricsScope = 'broker' | 'topic';

const SPARK_WIDTH = 120;
const SPARK_HEIGHT = 28;
const SPARK_PAD = 3;

/** A series with its sparkline laid out. */
export interface MetricRow {
  series: MetricSeries;
  labels: string;
  path: string;
  xs: number[];
  ys: number[];
}

/**
 * Metrics tab of the broker and topic pages: recent values from the Confluent metrics topics, or a guide to
 * turning those on when the cluster doesn't have them.
 */
@Component({
  selector: 'app-entity-metrics',
  imports: [CommonModule, FormsModule, PaginationComponent],
  templateUrl: './entity-metrics.component.html',
  styleUrl: './entity-metrics.component.scss'
})
export class EntityMetricsComponent implements OnInit {

  @Input({ required: true }) scope!: MetricsScope;
  @Input({ required: true }) target!: string;

  readonly sparkWidth = SPARK_WIDTH;
  readonly sparkHeight = SPARK_HEIGHT;
  readonly telemetryTopic = '_confluent-telemetry-metrics';
  readonly metricsTopic = '_confluent-metrics';

  metrics?: EntityMetrics;
  rows: MetricRow[] = [];
  errors: Map<string, Error> = new Map();
  loading = false;
  loaded = false;
  searchTerm = '';
  readonly pageSize = 15;
  page = 1;
  /** The sparkline point under the pointer. */
  hover?: { row: MetricRow; index: number };

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    const request: Observable<GenericResponse<EntityMetrics>> = this.scope === 'broker'
      ? this.apiService.getBrokerMetrics(this.target)
      : this.apiService.getTopicMetrics(this.target);
    request.subscribe({
      next: (res: GenericResponse<EntityMetrics>) => {
        this.metrics = res.data;
        this.rows = (res.data?.series ?? []).map(series => this.toRow(series));
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.filteredRows.length / this.pageSize)));
        this.errors.delete('metrics');
        this.loading = false;
        this.loaded = true;
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('metrics', this.commonService.prepareError(res.error?.error, '500', 'Failed to read the metrics topics!'));
        this.loading = false;
        this.loaded = true;
      }
    });
  }

  get sources(): MetricSource[] {
    return this.metrics?.sources ?? [];
  }

  /** At least one metrics topic exists, so there's something to read. */
  get available(): boolean {
    return this.sources.some(source => source.exists);
  }

  get allAvailable(): boolean {
    return this.sources.length > 0 && this.sources.every(source => source.exists);
  }

  get readableSources(): MetricSource[] {
    return this.sources.filter(source => source.exists && !source.error);
  }

  get scannedRecords(): number {
    return this.sources.reduce((total, source) => total + (source.scannedRecords ?? 0), 0);
  }

  get filteredRows(): MetricRow[] {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) return this.rows;
    return this.rows.filter(row => row.series.name.toLowerCase().includes(term) || row.labels.toLowerCase().includes(term)
      || (row.series.broker ?? '').includes(term));
  }

  get pagedRows(): MetricRow[] {
    const start = (this.page - 1) * this.pageSize;
    return this.filteredRows.slice(start, start + this.pageSize);
  }

  onSearch(): void {
    this.page = 1;
  }

  trackRow(_: number, row: MetricRow): string {
    return `${row.series.source}|${row.series.name}|${row.series.kind}|${row.series.broker}|${row.labels}`;
  }

  /** Picks the point nearest the pointer on a row's sparkline. */
  hoverSpark(event: MouseEvent, row: MetricRow): void {
    const svg = event.currentTarget as SVGElement;
    const box = svg.getBoundingClientRect();
    const x = (event.clientX - box.left) / box.width * SPARK_WIDTH;
    let index = 0;
    row.xs.forEach((px, i) => { if (Math.abs(px - x) < Math.abs(row.xs[index] - x)) index = i; });
    this.hover = { row, index };
  }

  isHovered(row: MetricRow): boolean {
    return this.hover?.row === row;
  }

  formatValue(value: number): string {
    if (!Number.isFinite(value)) return String(value);
    const abs = Math.abs(value);
    if (abs >= 1e6) return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 2 }).format(value);
    return value.toLocaleString(undefined, { maximumFractionDigits: abs < 1 ? 4 : 2 });
  }

  formatTime(timestamp?: number): string {
    return timestamp == null ? 'N/A' : new Date(timestamp).toLocaleString();
  }

  /** The last part of a metric name, which says what it measures; the rest is shown smaller. */
  shortName(name: string): string {
    const parts = name.split(/[:/]/);
    return parts[parts.length - 1] || name;
  }

  namespace(name: string): string {
    const short = this.shortName(name);
    return name.length > short.length ? name.slice(0, name.length - short.length - 1) : '';
  }

  private toRow(series: MetricSeries): MetricRow {
    const labels = Object.entries(series.labels ?? {}).map(([k, v]) => `${k}=${v}`).join(', ');
    const points = series.points ?? [];
    const values = points.map(p => p.v);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const first = points.length ? points[0].t : 0;
    const span = points.length > 1 ? points[points.length - 1].t - first : 0;
    const xs = points.map((p, i) => SPARK_PAD + (span > 0 ? (p.t - first) / span : (points.length > 1 ? i / (points.length - 1) : 0.5))
      * (SPARK_WIDTH - 2 * SPARK_PAD));
    // A flat series sits mid-height rather than on the floor.
    const ys = values.map(v => max > min
      ? SPARK_HEIGHT - SPARK_PAD - (v - min) / (max - min) * (SPARK_HEIGHT - 2 * SPARK_PAD)
      : SPARK_HEIGHT / 2);
    const path = xs.map((x, i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ');
    return { series, labels, path, xs, ys };
  }

}
