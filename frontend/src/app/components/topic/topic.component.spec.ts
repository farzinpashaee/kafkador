import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { Subject, throwError, of } from 'rxjs';

import { TopicComponent } from './topic.component';
import { ApiService, DocumentationService, CommonService } from '../../services';
import { GenericResponse, Topic } from '../../models';

describe('TopicComponent', () => {
  let component: TopicComponent;
  let fixture: ComponentFixture<TopicComponent>;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;
  let messages$: Subject<string>;

  const topic: Topic = {
    id: '1',
    name: 'TEST_TOPIC_1',
    partitions: 1,
    internal: false,
    replicatorFactor: 1,
    config: []
  };

  beforeEach(async () => {
    messages$ = new Subject<string>();
    apiServiceSpy = jasmine.createSpyObj('ApiService', [
      'getTopicDetails', 'consumeTopicMessages', 'produceTopicMessage'
    ]);
    apiServiceSpy.getTopicDetails.and.returnValue(of({ data: topic } as GenericResponse<Topic>));
    apiServiceSpy.consumeTopicMessages.and.returnValue(messages$.asObservable());

    await TestBed.configureTestingModule({
      imports: [TopicComponent],
      providers: [
        { provide: ApiService, useValue: apiServiceSpy },
        { provide: DocumentationService, useValue: {} },
        { provide: CommonService, useValue: new CommonService() },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ name: topic.name }) } }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TopicComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts listening and appends the latest consumed message to the front', () => {
    component.startListening();
    expect(component.listening).toBeTrue();

    messages$.next('{ Event 1 Received }');
    messages$.next('{ Event 2 Received }');

    expect(component.consumedMessages).toEqual(['{ Event 2 Received }', '{ Event 1 Received }']);
  });

  it('stops listening and unsubscribes from the stream', () => {
    component.startListening();
    component.stopListening();

    expect(component.listening).toBeFalse();
    expect(messages$.observed).toBeFalse();
  });

  it('toggleListening flips between start and stop', () => {
    component.toggleListening();
    expect(component.listening).toBeTrue();

    component.toggleListening();
    expect(component.listening).toBeFalse();
  });

  it('surfaces an error and stops listening when the stream errors out', () => {
    component.startListening();
    messages$.error(new Error('boom'));

    expect(component.listening).toBeFalse();
    expect(component.errors.get('testTopicConsumer')?.message).toBe('boom');
  });

  it('sends the producer input and clears it on success', () => {
    apiServiceSpy.produceTopicMessage.and.returnValue(of({ data: { value: 'hello' } } as GenericResponse<any>));
    component.producerEvent = '{ hello: "world" }';

    component.sendEvent();

    expect(apiServiceSpy.produceTopicMessage).toHaveBeenCalledWith(topic.name, { value: '{ hello: "world" }' });
    expect(component.producerEvent).toBe('');
    expect(component.flags.get('sendingEvent')).toBeFalse();
  });

  it('does not send blank producer input', () => {
    component.producerEvent = '   ';
    component.sendEvent();
    expect(apiServiceSpy.produceTopicMessage).not.toHaveBeenCalled();
  });

  it('surfaces an error when sending fails', () => {
    apiServiceSpy.produceTopicMessage.and.returnValue(throwError(() => ({ error: null })));
    component.producerEvent = '{ bad: true }';

    component.sendEvent();

    expect(component.errors.get('sendEvent')?.message).toBe('Failed to send event!');
    expect(component.flags.get('sendingEvent')).toBeFalse();
  });

  it('unsubscribes from the stream on destroy', () => {
    component.startListening();
    component.ngOnDestroy();
    expect(messages$.observed).toBeFalse();
  });
});
