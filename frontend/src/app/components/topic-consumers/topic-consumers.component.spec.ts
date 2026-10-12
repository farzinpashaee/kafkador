import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { TopicConsumersComponent } from './topic-consumers.component';
import { ApiService } from '../../services';
import { TopicConsumerGroup } from '../../models';

describe('TopicConsumersComponent', () => {
  let component: TopicConsumersComponent;
  let fixture: ComponentFixture<TopicConsumersComponent>;
  let api: jasmine.SpyObj<ApiService>;

  const groups: TopicConsumerGroup[] = [
    { groupId: 'billing', state: 'Stable', memberCount: 2, groupMemberCount: 3, partitionCount: 6, lag: 40 },
    { groupId: 'audit', state: 'Empty', memberCount: 0, groupMemberCount: 0, partitionCount: 6, lag: 1200 },
    { groupId: 'search-indexer', state: 'PreparingRebalance', memberCount: 1, groupMemberCount: 1, partitionCount: 2 }
  ];

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getTopicConsumerGroups']);
    api.getTopicConsumerGroups.and.returnValue(of({ data: groups } as any));

    await TestBed.configureTestingModule({
      imports: [TopicConsumersComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(TopicConsumersComponent);
    component = fixture.componentInstance;
    component.topicName = 'orders';
    fixture.detectChanges();
  });

  it('lists the groups reading the topic, sorted by group id', () => {
    expect(api.getTopicConsumerGroups).toHaveBeenCalledWith('orders');
    expect(component.filteredGroups.map(g => g.groupId)).toEqual(['audit', 'billing', 'search-indexer']);
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody > tr');
    expect(rows.length).toBe(3);
    expect(rows[1].textContent).toContain('2 of 3');
    expect(rows[2].textContent).toContain('N/A');
  });

  it('sums the known lag and counts groups with members on the topic', () => {
    expect(component.totalLag).toBe(1240);
    expect(component.activeGroupCount).toBe(2);
  });

  it('searches and sorts, with unknown lag last', () => {
    component.searchTerm = 'BILL';
    expect(component.filteredGroups.map(g => g.groupId)).toEqual(['billing']);
    component.searchTerm = '';
    component.sortBy('lag');
    expect(component.filteredGroups.map(g => g.groupId)).toEqual(['billing', 'audit', 'search-indexer']);
  });

  it('exports the listed groups', () => {
    expect(component.exportFormats.length).toBeGreaterThan(0);
  });

  it('shows the error when the groups cannot be read', () => {
    api.getTopicConsumerGroups.and.returnValue(throwError(() => ({ status: 404, error: { error: { message: 'Topic does not exist' } } })));
    component.load();
    expect(component.errors.get('groups')).toBeTruthy();
    expect(component.loading).toBeFalse();
  });
});
