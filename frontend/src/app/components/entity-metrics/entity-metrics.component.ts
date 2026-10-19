import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService, CommonService } from '../../services';
import { EntityMetrics, Error, GenericResponse, MetricSeries, MetricSource } from '../../models';

export type MetricsScope = 'broker' | 'topic';

const SPARK_WIDTH = 120;
const SPARK_HEIGHT = 28;
const SPARK_PAD = 3;

/** A series with its sparkline laid out. */
export interface MetricRow {
  series: MetricSeries;
  labels: string;
  /** The part of the name a metric is grouped by, e.g. broker_topic or BrokerTopicMetrics. */
  group: string;
  /** The rest of the name, which says what it measures. */
  title: string;
  /** Every value read is 0. */
  zero: boolean;
  min: number;
  max: number;
  path: string;
  xs: number[];
  ys: number[];
}

export interface MetricGroup {
  name: string;
  rows: MetricRow[];
}

export type KpiFormat = 'bytesRate' | 'rate' | 'count' | 'ratio';

/** A headline number, read from the first of its name patterns that any series matches. */
interface KpiDefinition {
  label: string;
  patterns: RegExp[];
  format: KpiFormat;
  /** How the matched series (one per broker, or per label) add up. */
  combine: 'sum' | 'avg';
  tone?: (value: number) => 'danger' | 'warning' | undefined;
}

export interface Kpi {
  label: string;
  value: string;
  tone?: 'danger' | 'warning';
  /** It has a threshold, so a health dot tells whether it's crossed. */
  checked: boolean;
  format: KpiFormat;
  /** The series it was read from; a sparkline is drawn when there's just one. */
  rows: MetricRow[];
}

// Telemetry Reporter names (io.confluent.kafka.server/group/metric/...) first, then Metrics Reporter ones
// (kafka.server:Type:Name); the first pattern with matches wins, so a cluster with both isn't counted twice.
const KPIS: KpiDefinition[] = [
  { label: 'Bytes in', format: 'bytesRate', combine: 'sum',
    patterns: [/\/broker_topic\/bytes_in\/rate\/1_min$/, /\/broker_topic\/bytes_in\/rate$/, /:BrokerTopicMetrics:BytesInPerSec$/] },
  { label: 'Bytes out', format: 'bytesRate', combine: 'sum',
    patterns: [/\/broker_topic\/bytes_out\/rate\/1_min$/, /\/broker_topic\/bytes_out\/rate$/, /:BrokerTopicMetrics:BytesOutPerSec$/] },
  { label: 'Messages in', format: 'rate', combine: 'sum',
    patterns: [/\/broker_topic\/messages_in\/rate\/1_min$/, /\/broker_topic\/messages_in\/rate$/, /:BrokerTopicMetrics:MessagesInPerSec$/] },
  { label: 'Under-replicated partitions', format: 'count', combine: 'sum', tone: v => v > 0 ? 'danger' : undefined,
    patterns: [/\/under_replicated_partitions$/, /:ReplicaManager:UnderReplicatedPartitions$/] },
  { label: 'Offline partitions', format: 'count', combine: 'sum', tone: v => v > 0 ? 'danger' : undefined,
    patterns: [/\/offline_partitions_count$/, /:KafkaController:OfflinePartitionsCount$/] },
  { label: 'Partitions', format: 'count', combine: 'sum',
    patterns: [/\/replica_manager\/partition_count$/, /:ReplicaManager:PartitionCount$/] },
  { label: 'Leaders', format: 'count', combine: 'sum',
    patterns: [/\/replica_manager\/leader_count$/, /:ReplicaManager:LeaderCount$/] },
  { label: 'Request handler idle', format: 'ratio', combine: 'avg', tone: v => ratio(v) < 0.2 ? 'warning' : undefined,
    // Not the Telemetry Reporter's request_handler_avg_idle_percent/rate: it isn't a fraction of time idle.
    patterns: [/:KafkaRequestHandlerPool:RequestHandlerAvgIdlePercent$/] },
  { label: 'CPU', format: 'ratio', combine: 'avg', tone: v => ratio(v) > 0.8 ? 'warning' : undefined,
    patterns: [/\/server\/linux_system_cpu_utilization_1m$/] }
];

/** Idle figures come as a fraction or as a percentage depending on the reporter. */
function ratio(value: number): number {
  return value > 1 ? value / 100 : value;
}

/**
 * Metrics tab of the broker and topic pages: headline numbers, then every series grouped by what it's about, from
 * the Confluent metrics topics; or a guide to turning those on when the cluster doesn't have them.
 */
@Component({
  selector: 'app-entity-metrics',
  imports: [CommonModule, FormsModule],
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
  kpis: Kpi[] = [];
  groups: MetricGroup[] = [];
  /** Series hidden because every value is 0, among those matching the search. */
  zeroCount = 0;
  showZero = false;
  /** Seconds between the backend reading the topics and this page getting the answer. */
  readAge?: number;
  expanded = new Set<string>();
  errors: Map<string, Error> = new Map();
  loading = false;
  loaded = false;
  searchTerm = '';
  /** The sparkline point under the pointer. */
  hover?: { row: MetricRow; index: number };
  kpiHover?: { kpi: Kpi; index: number };

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit(): void {
    this.load();
  }

  /** @param refresh read the metrics topics again instead of taking a read the backend made moments ago */
  load(refresh = false): void {
    this.loading = true;
    const request: Observable<GenericResponse<EntityMetrics>> = this.scope === 'broker'
      ? this.apiService.getBrokerMetrics(this.target, refresh)
      : this.apiService.getTopicMetrics(this.target, refresh);
    request.subscribe({
      next: (res: GenericResponse<EntityMetrics>) => {
        this.metrics = res.data;
        this.readAge = res.data?.readAt ? Math.max(0, Math.round((Date.now() - res.data.readAt) / 1000)) : undefined;
        this.rows = (res.data?.series ?? []).map(series => this.toRow(series));
        this.kpis = this.toKpis(this.rows);
        this.updateGroups();
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

  get shownCount(): number {
    return this.groups.reduce((total, group) => total + group.rows.length, 0);
  }

  onSearch(): void {
    this.updateGroups();
  }

  toggleZero(): void {
    this.showZero = !this.showZero;
    this.updateGroups();
  }

  isExpanded(group: MetricGroup): boolean {
    // A search opens every group that matches, and a lone group needs no click.
    return !!this.searchTerm.trim() || this.groups.length === 1 || this.expanded.has(group.name);
  }

  toggleGroup(group: MetricGroup): void {
    if (this.expanded.has(group.name)) this.expanded.delete(group.name);
    else this.expanded.add(group.name);
  }

  setAllExpanded(open: boolean): void {
    this.expanded = new Set(open ? this.groups.map(group => group.name) : []);
  }

  trackRow(_: number, row: MetricRow): string {
    return `${row.series.source}|${row.series.name}|${row.series.kind}|${row.series.broker}|${row.labels}`;
  }

  trackGroup(_: number, group: MetricGroup): string {
    return group.name;
  }

  /** Picks the point nearest the pointer on a row's sparkline. */
  hoverSpark(event: MouseEvent, row: MetricRow): void {
    this.hover = { row, index: this.nearestPoint(event, row) };
  }

  isHovered(row: MetricRow): boolean {
    return this.hover?.row === row;
  }

  /** Same for a headline sparkline, tracked apart so its series' table row doesn't light up too. */
  hoverKpi(event: MouseEvent, kpi: Kpi): void {
    this.kpiHover = { kpi, index: this.nearestPoint(event, kpi.rows[0]) };
  }

  private nearestPoint(event: MouseEvent, row: MetricRow): number {
    const svg = event.currentTarget as SVGElement;
    const box = svg.getBoundingClientRect();
    const x = (event.clientX - box.left) / box.width * SPARK_WIDTH;
    let index = 0;
    row.xs.forEach((px, i) => { if (Math.abs(px - x) < Math.abs(row.xs[index] - x)) index = i; });
    return index;
  }

  formatValue(value: number): string {
    if (!Number.isFinite(value)) return String(value);
    const abs = Math.abs(value);
    if (abs >= 1e6) return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 2 }).format(value);
    return value.toLocaleString(undefined, { maximumFractionDigits: abs < 1 ? 4 : 2 });
  }

  formatBytes(value: number): string {
    if (!Number.isFinite(value)) return String(value);
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let unit = 0;
    let scaled = Math.abs(value);
    while (scaled >= 1024 && unit < units.length - 1) { scaled /= 1024; unit++; }
    return `${value < 0 ? '-' : ''}${scaled.toLocaleString(undefined, { maximumFractionDigits: scaled < 10 && unit ? 1 : 0 })} ${units[unit]}`;
  }

  /** A value in the unit its name implies: bytes, per second, or plain. */
  formatMetric(value: number, series: MetricSeries): string {
    const perSecond = series.kind === 'rate' || /rate|PerSec/i.test(series.name);
    const text = /bytes/i.test(series.name) ? this.formatBytes(value) : this.formatValue(value);
    return perSecond ? `${text}/s` : text;
  }

  formatTime(timestamp?: number): string {
    return timestamp == null ? 'N/A' : new Date(timestamp).toLocaleString();
  }

  /** Underscores read as spaces in group names and titles. */
  humanize(text: string): string {
    return text.replace(/_/g, ' ');
  }

  private updateGroups(): void {
    const matching = this.filteredRows;
    const shown = this.showZero ? matching : matching.filter(row => !row.zero);
    this.zeroCount = matching.length - matching.filter(row => !row.zero).length;
    const byGroup = new Map<string, MetricRow[]>();
    shown.forEach(row => byGroup.set(row.group, [...(byGroup.get(row.group) ?? []), row]));
    this.groups = [...byGroup.entries()]
      .map(([name, rows]) => ({ name, rows }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  private toKpis(rows: MetricRow[]): Kpi[] {
    const kpis: Kpi[] = [];
    for (const definition of KPIS) {
      const matched = definition.patterns.map(pattern => rows.filter(row => pattern.test(row.series.name)))
        .find(found => found.length > 0);
      if (!matched) continue;
      const total = matched.reduce((sum, row) => sum + row.series.latest, 0);
      const value = definition.combine === 'avg' ? total / matched.length : total;
      kpis.push({ label: definition.label, value: this.formatKpi(value, definition.format), tone: definition.tone?.(value),
        checked: !!definition.tone, format: definition.format, rows: matched });
    }
    return kpis;
  }

  formatKpi(value: number, format: KpiFormat): string {
    switch (format) {
      case 'bytesRate': return `${this.formatBytes(value)}/s`;
      case 'rate': return `${this.formatValue(value)}/s`;
      case 'count': return Math.round(value).toLocaleString();
      case 'ratio': return `${(ratio(value) * 100).toFixed(0)}%`;
    }
  }

  /** Splits a name into the group it belongs to and what it measures. */
  private describe(name: string): { group: string; title: string } {
    const parts = name.split(/[:/]/).filter(part => part);
    if (parts.length >= 3) return { group: parts[1], title: parts.slice(2).join(' / ') };
    if (parts.length === 2) return { group: parts[0], title: parts[1] };
    return { group: 'other', title: name };
  }

  private toRow(series: MetricSeries): MetricRow {
    const labels = Object.entries(series.labels ?? {}).map(([k, v]) => `${k}=${v}`).join(', ');
    const points = series.points ?? [];
    const values = points.map(p => p.v);
    const min = values.length ? Math.min(...values) : series.latest;
    const max = values.length ? Math.max(...values) : series.latest;
    const first = points.length ? points[0].t : 0;
    const span = points.length > 1 ? points[points.length - 1].t - first : 0;
    const xs = points.map((p, i) => SPARK_PAD + (span > 0 ? (p.t - first) / span : (points.length > 1 ? i / (points.length - 1) : 0.5))
      * (SPARK_WIDTH - 2 * SPARK_PAD));
    // A flat series sits mid-height rather than on the floor.
    const ys = values.map(v => max > min
      ? SPARK_HEIGHT - SPARK_PAD - (v - min) / (max - min) * (SPARK_HEIGHT - 2 * SPARK_PAD)
      : SPARK_HEIGHT / 2);
    const path = xs.map((x, i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${ys[i].toFixed(1)}`).join(' ');
    const zero = series.latest === 0 && values.every(v => v === 0);
    return { series, labels, ...this.describe(series.name), zero, min, max, path, xs, ys };
  }

}
