import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { TopicsComponent } from './topics.component';
import { ApiService, LocalStorageService } from '../../services';
import { TopicOverview } from '../../models';

describe('TopicsComponent', () => {
  let component: TopicsComponent;
  let fixture: ComponentFixture<TopicsComponent>;
  let storage: Map<string, unknown>;

  const topic = (name: string, overrides: Partial<TopicOverview> = {}): TopicOverview => ({
    name, internal: name.startsWith('_'), partitionCount: 1, replicationFactor: 3, outOfSyncReplicaCount: 0,
    messageCount: 0, sizeBytes: 0, ...overrides
  });

  const topics: TopicOverview[] = [
    topic('__consumer_offsets', { partitionCount: 50, messageCount: undefined, sizeBytes: 10240 }),
    topic('_schemas', { messageCount: undefined, sizeBytes: 2048 }),
    topic('orders', { partitionCount: 3, messageCount: 120, sizeBytes: 5000 }),
    topic('payments', { partitionCount: 6, messageCount: 7, outOfSyncReplicaCount: 2 })
  ];

  beforeEach(async () => {
    storage = new Map();
    const api = jasmine.createSpyObj<ApiService>('ApiService', ['getTopicsOverview', 'getChart']);
    api.getTopicsOverview.and.returnValue(of({ data: topics } as any));
    api.getChart.and.returnValue(throwError(() => ({ status: 428 })));
    const localStorage = {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: unknown) => storage.set(key, value)
    };

    await TestBed.configureTestingModule({
      imports: [TopicsComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }, { provide: LocalStorageService, useValue: localStorage }]
    }).compileComponents();

    fixture = TestBed.createComponent(TopicsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('lists every topic, internal ones included, sorted by name', () => {
    expect(component.filteredTopics.map(t => t.name)).toEqual(['__consumer_offsets', '_schemas', 'orders', 'payments']);
    expect(component.internalTopicCount).toBe(2);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('IN');
  });

  it('hides internal topics when the filter is switched off and remembers the choice', () => {
    component.setShowInternal(false);

    expect(component.filteredTopics.map(t => t.name)).toEqual(['orders', 'payments']);
    expect(storage.get('topics.showInternal')).toBeFalse();
  });

  it('searches topic names after a short pause', fakeAsync(() => {
    component.filter.setValue('PAY');
    tick(250);

    expect(component.filteredTopics.map(t => t.name)).toEqual(['payments']);
  }));

  it('sorts numerically and keeps unknown message counts last', () => {
    component.sortBy('messageCount');
    expect(component.filteredTopics.map(t => t.name)).toEqual(['payments', 'orders', '__consumer_offsets', '_schemas']);

    component.sortBy('partitionCount');
    component.sortBy('partitionCount');
    expect(component.filteredTopics[0].name).toBe('__consumer_offsets');
    expect(component.ariaSort('partitionCount')).toBe('descending');
  });

  it('shows N/A for unknown sizes and exports the listed topics', () => {
    expect(component.formatSize(undefined)).toBe('N/A');
    expect(component.formatSize(2048)).toBe('2.00 KB');

    const csv = component.exportContent('csv', [topics[2]]).split('\r\n');
    expect(csv[0]).toBe('Topic name,Topic ID,Internal,Partitions,Out of sync replicas,Replication factor,Number of messages,Size (bytes),Cleanup policy');
    expect(csv[1]).toBe('orders,,false,3,0,3,120,5000,');
    expect(JSON.parse(component.exportContent('json', [topics[2]]))[0]).toEqual(jasmine.objectContaining({ name: 'orders', messages: 120 }));
    const xml = new DOMParser().parseFromString(component.exportContent('xml', [topics[2]]), 'application/xml');
    expect(xml.querySelector('topics > topic > name')?.textContent).toBe('orders');
  });
});
