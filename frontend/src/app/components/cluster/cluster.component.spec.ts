import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ClusterComponent } from './cluster.component';
import { ApiService } from '../../services';
import { BrokerOverview, ClusterOverview } from '../../models';

describe('ClusterComponent', () => {
  let component: ClusterComponent;
  let fixture: ComponentFixture<ClusterComponent>;

  const broker = (id: string, overrides: Partial<BrokerOverview> = {}): BrokerOverview => ({
    id, host: `kafka${id}`, port: 29092, activeController: false, diskUsageBytes: 12450, logCount: 134,
    replicaCount: 134, inSyncReplicaCount: 134, leaderCount: 0, replicasSkew: 0, leadersSkew: -100, ...overrides
  });

  const overview: ClusterOverview = {
    brokerCount: 3, activeControllerId: '3', version: '3.9-IV0', controllerType: 'KRaft',
    partitionCount: 134, onlinePartitionCount: 134, underReplicatedPartitionCount: 0,
    replicaCount: 402, inSyncReplicaCount: 402, outOfSyncReplicaCount: 0,
    brokers: [broker('3', { activeController: true }), broker('1'), broker('2', { leaderCount: 134, leadersSkew: 200 })]
  };

  async function create(overviewResponse = of({ data: overview } as any)) {
    const api = jasmine.createSpyObj<ApiService>('ApiService', ['getClusterOverview']);
    api.getClusterOverview.and.returnValue(overviewResponse);

    await TestBed.configureTestingModule({
      imports: [ClusterComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(ClusterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('shows the cluster and partition summary', async () => {
    await create();
    const text = (fixture.nativeElement as HTMLElement).textContent!;

    expect(text).toContain('3.9-IV0');
    expect(text).toContain('KRaft');
    expect(text).toContain('402 of 402');
  });

  it('lists brokers sorted by id by default and toggles sort direction', async () => {
    await create();
    expect(component.sortedBrokers.map(b => b.id)).toEqual(['1', '2', '3']);

    component.sortBy('leaderCount');
    expect(component.sortedBrokers[0].leaderCount).toBe(0);
    component.sortBy('leaderCount');
    expect(component.sortedBrokers[0].id).toBe('2');
    expect(component.ariaSort('leaderCount')).toBe('descending');
  });

  it('formats disk usage and skew like the broker table expects', async () => {
    await create();

    expect(component.formatDiskUsage(broker('1'))).toBe('12.16 KB, 134 logs');
    expect(component.formatDiskUsage(broker('1', { diskUsageBytes: undefined }))).toBe('n/a');
    expect(component.formatSkew(0)).toBe('-');
    expect(component.formatSkew(undefined)).toBe('-');
    expect(component.formatSkew(-100)).toBe('-100.00%');
    expect(component.formatSkew(200)).toBe('+200.00%');
  });

  it('filters the table by any visible value and resets to the first page', async () => {
    await create();
    component.page = 2;

    component.searchTerm = 'KAFKA2';
    component.onSearch();
    expect(component.sortedBrokers.map(b => b.id)).toEqual(['2']);
    expect(component.page).toBe(1);

    component.searchTerm = 'controller';
    expect(component.sortedBrokers.map(b => b.id)).toEqual(['3']);

    component.searchTerm = 'no-such-broker';
    expect(component.sortedBrokers).toEqual([]);
  });

  it('exports the listed brokers as CSV, JSON and XML', async () => {
    await create();
    const rows = [broker('1', { host: 'a&b<c>', rack: 'r,1' })];

    const csv = component.exportContent('csv', rows).split('\r\n');
    expect(csv[0]).toBe('Broker ID,Active controller,Disk usage (bytes),Logs,In sync replicas,Replicas,Replicas skew (%),Leaders,Leaders skew (%),Port,Host,Rack');
    expect(csv[1]).toBe('1,false,12450,134,134,134,0,0,-100,29092,a&b<c>,"r,1"');

    const json = JSON.parse(component.exportContent('json', rows));
    expect(json).toEqual([jasmine.objectContaining({ brokerId: '1', diskUsageBytes: 12450, leadersSkewPercent: -100, host: 'a&b<c>' })]);

    const xml = new DOMParser().parseFromString(component.exportContent('xml', rows), 'application/xml');
    expect(xml.querySelector('parsererror')).toBeNull();
    expect(xml.querySelector('broker > host')?.textContent).toBe('a&b<c>');
    expect(xml.querySelectorAll('brokers > broker').length).toBe(1);
  });

  it('shows an error when the overview cannot be loaded', async () => {
    await create(throwError(() => ({ status: 500, error: {} })));

    expect(component.errors.get('getOverview')).toBeTruthy();
    expect(component.overview).toBeUndefined();
  });
});
