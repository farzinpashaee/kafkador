import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ConsumerComponent } from './consumer.component';
import { ApiService, CommonService } from '../../services';
import { ConsumerGroupDetails, OffsetResetResult } from '../../models';

describe('ConsumerComponent', () => {
  let component: ConsumerComponent;
  let fixture: ComponentFixture<ConsumerComponent>;
  let api: jasmine.SpyObj<ApiService>;

  const group = {
    id: 'billing', state: 'Empty', memberCount: 0, topicCount: 1, partitionCount: 2, totalLag: 30,
    topics: [{ name: 'orders', partitionCount: 2, memberCount: 0, lag: 30, partitions: [
      { partition: 1, committedOffset: 5, endOffset: 20, lag: 15 },
      { partition: 0, committedOffset: 5, endOffset: 20, lag: 15 }
    ] }]
  } as unknown as ConsumerGroupDetails;

  const preview = (blockedReason: string | null = null): OffsetResetResult => ({
    applied: false, blockedReason,
    changes: [{ topic: 'orders', partition: 0, currentOffset: 5, newOffset: 0, endOffset: 20, lagAfter: 20 }]
  });

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getConsumerGroupDetails', 'resetConsumerGroupOffsets']);
    api.getConsumerGroupDetails.and.returnValue(of({ data: group } as any));
    await TestBed.configureTestingModule({
      imports: [ConsumerComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'billing' } } } }
      ]
    }).compileComponents();
    // Bootstrap's modal API isn't loaded in unit tests.
    spyOn(TestBed.inject(CommonService), 'hideModal');
    fixture = TestBed.createComponent(ConsumerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('builds the reset request from the dialog', () => {
    component.openResetDialog();
    expect(component.buildResetRequest(true)).toEqual({
      topic: undefined, partitions: [], target: 'EARLIEST', timestamp: undefined, offset: undefined, dryRun: true
    });

    component.resetTopic = 'orders';
    component.resetPartition = 1;
    component.resetTarget = 'TIMESTAMP';
    component.resetTime = '2026-10-01T12:00:00';
    const request = component.buildResetRequest(false);
    expect(request.topic).toBe('orders');
    expect(request.partitions).toEqual([1]);
    expect(request.timestamp).toBe(new Date('2026-10-01T12:00:00').getTime());
    expect(request.dryRun).toBeFalse();
    expect(component.resetPartitions).toEqual([0, 1]);
  });

  it('asks for a time or an offset before previewing', () => {
    component.resetTarget = 'TIMESTAMP';
    expect(component.resetInputError).toContain('date and time');
    component.resetTarget = 'OFFSET';
    component.resetOffset = -1;
    expect(component.resetInputError).toContain('offset');

    component.previewReset();
    expect(api.resetConsumerGroupOffsets).not.toHaveBeenCalled();
  });

  it('previews, then applies what was previewed and reloads the group', () => {
    api.resetConsumerGroupOffsets.and.returnValue(of({ data: preview() } as any));
    component.previewReset();
    expect(api.resetConsumerGroupOffsets).toHaveBeenCalledWith('billing', jasmine.objectContaining({ dryRun: true }));
    expect(component.canApplyReset).toBeTrue();

    api.getConsumerGroupDetails.calls.reset();
    api.resetConsumerGroupOffsets.and.returnValue(of({ data: { ...preview(), applied: true } } as any));
    component.applyReset();

    expect(api.resetConsumerGroupOffsets).toHaveBeenCalledWith('billing', jasmine.objectContaining({ dryRun: false }));
    expect(TestBed.inject(CommonService).hideModal).toHaveBeenCalledWith('resetOffsetsModal');
    expect(api.getConsumerGroupDetails).toHaveBeenCalled();
    expect(component.resetDone).toBe('Reset 1 partition to earliest.');
  });

  it('will not apply while the group is active, or after the choices change', () => {
    api.resetConsumerGroupOffsets.and.returnValue(of({ data: preview("Consumer group 'billing' has 2 active members.") } as any));
    component.previewReset();
    fixture.detectChanges();
    expect(component.canApplyReset).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2 active members');

    api.resetConsumerGroupOffsets.and.returnValue(of({ data: preview() } as any));
    component.previewReset();
    component.resetTarget = 'LATEST';
    component.onResetChange();
    expect(component.canApplyReset).toBeFalse();
  });

  it('shows why the server refused the reset', () => {
    api.resetConsumerGroupOffsets.and.returnValue(throwError(() => ({ status: 400,
      error: { error: { code: '400', message: "Topic 'orders' has no partition 9.", datetime: '' } } })));
    component.previewReset();
    expect(component.errors.get('reset')?.message).toContain('no partition 9');
  });
});
