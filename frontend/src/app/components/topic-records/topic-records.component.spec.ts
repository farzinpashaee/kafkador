import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { MAX_LIVE_RECORDS, TopicRecordsComponent } from './topic-records.component';
import { ApiService, CommonService } from '../../services';
import { TopicRecord } from '../../models';

describe('TopicRecordsComponent', () => {
  let component: TopicRecordsComponent;
  let fixture: ComponentFixture<TopicRecordsComponent>;
  let api: jasmine.SpyObj<ApiService>;

  const record = (partition: number, offset: number, timestamp: number, overrides: Partial<TopicRecord> = {}): TopicRecord => ({
    partition, offset, timestamp, timestampType: 'CreateTime', key: `key-${offset}`, value: `{"n":${offset}}`,
    keySize: 5, valueSize: 6, headers: [], ...overrides
  });

  const records = [record(1, 4, 3000), record(0, 9, 2000), record(0, 8, 1000, { key: null, keySize: -1 })];

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['searchTopicRecords', 'produceTopicRecord', 'listenTopicRecords', 'getSchemaSubjects']);
    api.getSchemaSubjects.and.returnValue(of({ data: { configured: true, subjects: [{ name: 'payments-value' }] } } as any));
    api.produceTopicRecord.and.returnValue(of({ data: { partition: 1, offset: 81, timestamp: 5000 } } as any));
    api.searchTopicRecords.and.returnValue(of({ data: { records, scannedCount: 3, scanLimitReached: false } } as any));

    await TestBed.configureTestingModule({
      imports: [TopicRecordsComponent],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();

    // Bootstrap's modal API isn't loaded in unit tests.
    spyOn(TestBed.inject(CommonService), 'hideModal');
    fixture = TestBed.createComponent(TopicRecordsComponent);
    component = fixture.componentInstance;
    component.topicName = 'orders';
    component.partitionCount = 2;
    fixture.detectChanges();
  });

  it('reads the newest records of every partition on open', () => {
    expect(api.searchTopicRecords).toHaveBeenCalledWith('orders', jasmine.objectContaining({
      seekType: 'NEWEST', partitions: [], limit: 100, keySerde: 'STRING', valueSerde: 'STRING', filters: []
    }));
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody > tr');
    expect(rows.length).toBe(3);
    expect(rows[2].textContent).toContain('null');
  });

  it('reads with Schema Registry when the topic has a value or key subject', () => {
    api.getSchemaSubjects.and.returnValue(of({ data: { configured: true, subjects: [{ name: 'orders-value' }, { name: 'orders-key' }] } } as any));
    api.searchTopicRecords.calls.reset();

    component.ngOnInit();

    expect(api.searchTopicRecords).toHaveBeenCalledWith('orders', jasmine.objectContaining({ keySerde: 'SCHEMA_REGISTRY', valueSerde: 'SCHEMA_REGISTRY' }));
  });

  it('still reads records when the subject list is unavailable', () => {
    api.getSchemaSubjects.and.returnValue(throwError(() => ({ status: 500 })));
    api.searchTopicRecords.calls.reset();

    component.ngOnInit();

    expect(api.searchTopicRecords).toHaveBeenCalledWith('orders', jasmine.objectContaining({ keySerde: 'STRING', valueSerde: 'STRING' }));
  });

  it('produces JSON when the table reads with Schema Registry, which it cannot produce', () => {
    component.valueSerde = 'SCHEMA_REGISTRY';
    component.openProduceDialog();

    expect(component.produceValueSerde).toBe('JSON');
    expect(component.produceSerdes.map(s => s.value)).not.toContain('SCHEMA_REGISTRY');
  });

  it('waits for an offset before reading from one', () => {
    api.searchTopicRecords.calls.reset();
    component.seekType = 'OFFSET';
    component.onSeekTypeChange();
    component.load();
    expect(api.searchTopicRecords).not.toHaveBeenCalled();

    component.offset = 5;
    component.load();
    expect(api.searchTopicRecords).toHaveBeenCalledWith('orders', jasmine.objectContaining({ seekType: 'OFFSET', offset: 5 }));
  });

  it('sends the selected partitions and filters, and holds back filters without text', () => {
    component.togglePartition(1, true);
    expect(api.searchTopicRecords.calls.mostRecent().args[1].partitions).toEqual([1]);

    api.searchTopicRecords.calls.reset();
    component.addFilter();
    expect(component.canLoad).toBeFalse();
    component.filters[0].value = '  order-42 ';
    component.load();
    expect(api.searchTopicRecords.calls.mostRecent().args[1].filters)
      .toEqual([{ field: 'ANY', operator: 'CONTAINS', value: 'order-42' }]);
  });

  it('keeps the server order until a column is sorted', () => {
    expect(component.sortedRecords.map(r => r.offset)).toEqual([4, 9, 8]);
    component.sortBy('timestamp');
    expect(component.sortedRecords.map(r => r.offset)).toEqual([8, 9, 4]);
  });

  it('pretty-prints JSON bodies and leaves other text alone', () => {
    expect(component.formatBody('{"a":1}')).toBe('{\n  "a": 1\n}');
    expect(component.formatBody('{not json')).toBe('{not json');
    expect(component.formatBody(null)).toBe('null');
  });

  it('shows the error when reading fails', () => {
    api.searchTopicRecords.and.returnValue(throwError(() => ({ status: 400, error: { error: { message: 'Topic has no partition 7' } } })));
    component.load();
    expect(component.errors.get('records')).toBeTruthy();
    expect(component.loading).toBeFalse();
  });

  it('produces a record with null for an empty key, then closes and reloads', () => {
    api.searchTopicRecords.calls.reset();
    component.producePartition = 1;
    component.produceValueSerde = 'JSON';
    component.produceValue = '{"orderId":42}';
    component.produceHeaders = '{"trace-id": "abc", "attempt": 2}';
    component.produce();
    expect(api.produceTopicRecord).toHaveBeenCalledWith('orders', {
      partition: 1, key: null, value: '{"orderId":42}', keySerde: 'STRING', valueSerde: 'JSON',
      headers: { 'trace-id': 'abc', attempt: '2' }
    });
    expect(api.searchTopicRecords).toHaveBeenCalled();
    expect(component.produceValue).toBe('');
    expect(component.produceHeaders).toBe('{}');
  });

  it('keeps the contents and shows where the record went when asked to', () => {
    component.keepProduceContents = true;
    component.produceKey = 'k1';
    component.produceValue = 'hello';
    component.produce();
    expect(component.produceValue).toBe('hello');
    expect(component.produceResult).toEqual({ partition: 1, offset: 81, timestamp: 5000 });
  });

  it('blocks producing invalid JSON values and headers', () => {
    component.produceValueSerde = 'JSON';
    component.produceValue = '{oops';
    expect(component.produceValueError).toBe('Not valid JSON');
    expect(component.canProduce).toBeFalse();

    component.produceValue = '{}';
    component.produceHeaders = '["not", "an", "object"]';
    expect(component.parsedHeaders.error).toContain('JSON object');
    component.produceHeaders = '{"nested": {"a": 1}}';
    expect(component.parsedHeaders.error).toContain('"nested"');
    component.produce();
    expect(api.produceTopicRecord).not.toHaveBeenCalled();
  });

  it('keeps the dialog contents and shows the error when producing fails', () => {
    api.produceTopicRecord.and.returnValue(throwError(() => ({ status: 400, error: {} })));
    component.produceKey = 'k1';
    component.produceValue = 'hello';
    component.produce();
    expect(component.errors.get('produce')).toBeTruthy();
    expect(component.produceValue).toBe('hello');
    expect(component.producing).toBeFalse();
  });

  it('opens the dialog with the serdes the table is using', () => {
    component.keySerde = 'LONG';
    component.valueSerde = 'JSON';
    component.openProduceDialog();
    expect(component.produceKeySerde).toBe('LONG');
    expect(component.produceValueSerde).toBe('JSON');
  });

  describe('live mode', () => {
    let stream: Subject<TopicRecord[]>;

    beforeEach(() => {
      stream = new Subject<TopicRecord[]>();
      api.listenTopicRecords.and.returnValue(stream.asObservable());
    });

    it('clears the table and puts new records on top, newest first', () => {
      component.togglePartition(0, true);
      component.toggleLive();
      expect(api.listenTopicRecords).toHaveBeenCalledWith('orders',
        { partitions: [0], keySerde: 'STRING', valueSerde: 'STRING', filters: [] });
      expect(component.live).toBeTrue();
      expect(component.sortedRecords).toEqual([]);

      stream.next([]);
      expect(component.liveConnecting).toBeFalse();
      stream.next([record(0, 81, 6000), record(0, 82, 7000)]);
      stream.next([record(0, 83, 8000)]);
      expect(component.sortedRecords.map(r => r.offset)).toEqual([83, 82, 81]);
      expect(component.liveReceived).toBe(3);
    });

    it('locks reading while live and stops on request', () => {
      component.toggleLive();
      expect(component.canLoad).toBeFalse();
      api.searchTopicRecords.calls.reset();
      component.load();
      expect(api.searchTopicRecords).not.toHaveBeenCalled();

      component.toggleLive();
      expect(component.live).toBeFalse();
      expect(stream.observed).toBeFalse();
      expect(component.canLoad).toBeTrue();
    });

    it('keeps only the newest records', () => {
      component.toggleLive();
      stream.next(Array.from({ length: MAX_LIVE_RECORDS + 20 }, (_, i) => record(0, i, i)));
      expect(component.sortedRecords.length).toBe(MAX_LIVE_RECORDS);
      expect(component.sortedRecords[0].offset).toBe(MAX_LIVE_RECORDS + 19);
    });

    it('reports a dropped stream and leaves live mode', () => {
      component.toggleLive();
      stream.error(new Error('The live stream was disconnected.'));
      expect(component.live).toBeFalse();
      expect(component.errors.get('records')?.message).toBe('The live stream was disconnected.');
    });

    it('stops listening when the tab goes away', () => {
      component.toggleLive();
      fixture.destroy();
      expect(stream.observed).toBeFalse();
    });
  });
});
