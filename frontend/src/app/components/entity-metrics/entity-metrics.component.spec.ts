import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { EntityMetricsComponent } from './entity-metrics.component';
import { ApiService, CommonService } from '../../services';
import { EntityMetrics, GenericResponse, MetricSeries } from '../../models';

describe('EntityMetricsComponent', () => {
  let component: EntityMetricsComponent;
  let fixture: ComponentFixture<EntityMetricsComponent>;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;

  const series: MetricSeries = {
    name: 'kafka.server:BrokerTopicMetrics:MessagesInPerSec',
    source: '_confluent-metrics',
    kind: 'rate',
    broker: '1',
    labels: { partition: '0' },
    latest: 30,
    latestTimestamp: 3000,
    points: [{ t: 1000, v: 10 }, { t: 2000, v: 20 }, { t: 3000, v: 30 }]
  };

  function create(metrics: EntityMetrics, scope: 'broker' | 'topic' = 'topic') {
    apiServiceSpy.getTopicMetrics.and.returnValue(of({ data: metrics } as GenericResponse<EntityMetrics>));
    apiServiceSpy.getBrokerMetrics.and.returnValue(of({ data: metrics } as GenericResponse<EntityMetrics>));
    fixture = TestBed.createComponent(EntityMetricsComponent);
    component = fixture.componentInstance;
    component.scope = scope;
    component.target = scope === 'topic' ? 'orders' : '1';
    fixture.detectChanges();
  }

  beforeEach(async () => {
    apiServiceSpy = jasmine.createSpyObj('ApiService', ['getTopicMetrics', 'getBrokerMetrics']);
    await TestBed.configureTestingModule({
      imports: [EntityMetricsComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceSpy },
        { provide: CommonService, useValue: new CommonService() }
      ]
    }).compileComponents();
  });

  it('shows the setup guide when neither metrics topic exists', () => {
    create({
      sources: [
        { topic: '_confluent-telemetry-metrics', exists: false, scannedRecords: 0, undecodedRecords: 0, matchedSamples: 0 },
        { topic: '_confluent-metrics', exists: false, scannedRecords: 0, undecodedRecords: 0, matchedSamples: 0 }
      ],
      series: [],
      seriesLimitReached: false
    });

    const text = fixture.nativeElement.textContent as string;
    expect(component.available).toBeFalse();
    expect(text).toContain('ConfluentMetricsReporter');
    expect(text).toContain('TelemetryReporter');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('lists series with a sparkline when a metrics topic exists', () => {
    create({
      sources: [
        { topic: '_confluent-telemetry-metrics', exists: false, scannedRecords: 0, undecodedRecords: 0, matchedSamples: 0 },
        { topic: '_confluent-metrics', exists: true, scannedRecords: 3, undecodedRecords: 0, matchedSamples: 3, fromTimestamp: 1000, toTimestamp: 3000 }
      ],
      series: [series],
      seriesLimitReached: false
    });

    expect(apiServiceSpy.getTopicMetrics).toHaveBeenCalledWith('orders', false);
    expect(component.rows.length).toBe(1);
    expect(component.rows[0].title).toBe('MessagesInPerSec');
    expect(component.rows[0].group).toBe('BrokerTopicMetrics');
    const row = component.rows[0];
    expect(row.labels).toBe('partition=0');
    // Oldest point at the left edge and lowest, newest at the right edge and highest.
    expect(row.xs[0]).toBeLessThan(row.xs[2]);
    expect(row.ys[0]).toBeGreaterThan(row.ys[2]);
    expect(fixture.nativeElement.querySelector('path.spark-line')).not.toBeNull();
  });

  it('reads the broker endpoint for the broker scope', () => {
    create({ sources: [], series: [], seriesLimitReached: false }, 'broker');

    expect(apiServiceSpy.getBrokerMetrics).toHaveBeenCalledWith('1', false);
  });

  it('filters rows by the search term', () => {
    create({
      sources: [{ topic: '_confluent-metrics', exists: true, scannedRecords: 1, undecodedRecords: 0, matchedSamples: 1 }],
      series: [series, { ...series, name: 'kafka.log:Log:Size', kind: 'gauge' }],
      seriesLimitReached: false
    });

    component.searchTerm = 'log:size';
    expect(component.filteredRows.map(r => r.series.name)).toEqual(['kafka.log:Log:Size']);
  });

  it('sums headline numbers across brokers and flags under-replicated partitions', () => {
    const urp = (broker: string, latest: number): MetricSeries => ({ ...series, name: 'kafka.server:ReplicaManager:UnderReplicatedPartitions',
      kind: 'gauge', broker, labels: {}, latest, points: [{ t: 1000, v: latest }] });
    create({
      sources: [{ topic: '_confluent-metrics', exists: true, scannedRecords: 1, undecodedRecords: 0, matchedSamples: 1 }],
      series: [
        { ...series, name: 'io.confluent.kafka.server/broker_topic/bytes_in/rate/1_min', kind: 'gauge', latest: 2048 },
        urp('1', 0), urp('2', 3)
      ],
      seriesLimitReached: false
    });

    const bytesIn = component.kpis.find(kpi => kpi.label === 'Bytes in')!;
    expect(bytesIn.value).toBe('2 KB/s');
    const underReplicated = component.kpis.find(kpi => kpi.label === 'Under-replicated partitions')!;
    expect(underReplicated.value).toBe('3');
    expect(underReplicated.tone).toBe('danger');
    expect(fixture.nativeElement.querySelectorAll('.kpi-tile').length).toBe(2);
  });

  it('groups series by name and hides all-zero ones until asked', () => {
    const zero: MetricSeries = { ...series, name: 'io.confluent.kafka.server/broker_topic/mirror_bytes_in/rate', latest: 0,
      points: [{ t: 1000, v: 0 }, { t: 2000, v: 0 }] };
    const log: MetricSeries = { ...series, name: 'kafka.log:Log:Size', kind: 'gauge' };
    create({
      sources: [{ topic: '_confluent-metrics', exists: true, scannedRecords: 1, undecodedRecords: 0, matchedSamples: 1 }],
      series: [series, zero, log],
      seriesLimitReached: false
    });

    expect(component.groups.map(group => group.name)).toEqual(['BrokerTopicMetrics', 'Log']);
    expect(component.zeroCount).toBe(1);
    expect(component.rows.find(row => row.series === zero)!.title).toBe('mirror_bytes_in / rate');

    component.toggleZero();
    expect(component.groups.map(group => group.name)).toEqual(['broker_topic', 'BrokerTopicMetrics', 'Log']);
  });

  it('asks the backend to read again on refresh', () => {
    create({ sources: [], series: [], seriesLimitReached: false }, 'broker');

    component.load(true);

    expect(apiServiceSpy.getBrokerMetrics).toHaveBeenCalledWith('1', true);
  });

  it('formats bytes and rates by the metric name', () => {
    create({ sources: [], series: [], seriesLimitReached: false });

    expect(component.formatMetric(1536, { ...series, name: 'a/bytes_out/rate', kind: 'gauge' })).toBe('1.5 KB/s');
    expect(component.formatMetric(12, { ...series, name: 'kafka.log:Log:Size', kind: 'gauge' })).toBe('12');
  });
});
