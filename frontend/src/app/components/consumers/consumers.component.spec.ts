import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ConsumersComponent } from './consumers.component';
import { ApiService, LocalStorageService } from '../../services';
import { ConsumerGroup } from '../../models';

describe('ConsumersComponent', () => {
  let component: ConsumersComponent;
  let fixture: ComponentFixture<ConsumersComponent>;

  const groups: ConsumerGroup[] = [
    { id: 'orders-service', type: 'CONSUMER', coordinator: '1', groupState: 'STABLE', partitionAssignor: 'range', isSimpleConsumerGroup: false },
    { id: 'audit, "legacy"', type: 'CLASSIC', coordinator: '2', groupState: 'EMPTY', partitionAssignor: '', isSimpleConsumerGroup: true }
  ];

  beforeEach(async () => {
    const api = jasmine.createSpyObj<ApiService>('ApiService', ['getConsumerGroups', 'getTopics', 'getChart']);
    api.getConsumerGroups.and.returnValue(of({ body: { data: groups } } as any));
    api.getTopics.and.returnValue(of({ body: { data: [] } } as any));
    api.getChart.and.returnValue(throwError(() => ({ status: 428 })));

    await TestBed.configureTestingModule({
      imports: [ConsumersComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api },
        { provide: LocalStorageService, useValue: { getItem: () => null, setItem: () => {} } }]
    }).compileComponents();

    fixture = TestBed.createComponent(ConsumersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('exports the listed consumer groups', () => {
    const csv = component.exportContent('csv', groups).split('\r\n');
    expect(csv[0]).toBe('ID,Type,Coordinator,Simple consumer group,Partition assignor,Group state');
    expect(csv[1]).toBe('orders-service,CONSUMER,1,false,range,STABLE');
    expect(csv[2]).toBe('"audit, ""legacy""",CLASSIC,2,true,,EMPTY');
    expect(JSON.parse(component.exportContent('json', groups))[0])
      .toEqual(jasmine.objectContaining({ id: 'orders-service', simpleConsumerGroup: false, groupState: 'STABLE' }));
    const xml = new DOMParser().parseFromString(component.exportContent('xml', groups), 'application/xml');
    expect(xml.querySelectorAll('consumerGroups > consumerGroup').length).toBe(2);
    expect(xml.querySelector('consumerGroup > id')?.textContent).toBe('orders-service');
  });

  it('disables Export when no consumer group matches the search', () => {
    const exportButton = (): HTMLButtonElement =>
      Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
        .find(b => b.textContent?.trim() === 'Export')!;
    expect(exportButton().disabled).toBeFalse();

    component.filteredConsumerGroups = [];
    fixture.detectChanges();
    expect(exportButton().disabled).toBeTrue();
  });
});
