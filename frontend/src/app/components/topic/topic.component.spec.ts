import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { TopicComponent } from './topic.component';
import { ApiService, DocumentationService, CommonService } from '../../services';
import { GenericResponse, Topic } from '../../models';

describe('TopicComponent', () => {
  let component: TopicComponent;
  let fixture: ComponentFixture<TopicComponent>;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;

  const topic: Topic = {
    id: '1',
    name: 'TEST_TOPIC_1',
    partitions: 1,
    internal: false,
    replicatorFactor: 1,
    config: []
  };

  beforeEach(async () => {
    apiServiceSpy = jasmine.createSpyObj('ApiService', ['getTopicDetails', 'getTopicConsumerGroups', 'searchTopicRecords', 'getTopicMetrics']);
    apiServiceSpy.getTopicDetails.and.returnValue(of({ data: topic } as GenericResponse<Topic>));
    apiServiceSpy.getTopicConsumerGroups.and.returnValue(of({ data: [] } as any));
    apiServiceSpy.getTopicMetrics.and.returnValue(of({ data: { sources: [], series: [], seriesLimitReached: false } } as any));

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

  it('has Details, Records, Configurations, Consumers and Metrics tabs', () => {
    const tabs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.nav-link'))
      .map(tab => tab.textContent?.trim());
    expect(tabs).toEqual(['Details', 'Records', 'Configurations', 'Consumers', 'Metrics']);
  });

  it('only reads the metrics topics once the Metrics tab is opened', () => {
    expect(apiServiceSpy.getTopicMetrics).not.toHaveBeenCalled();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#metrics-tab')!.click();
    fixture.detectChanges();
    expect(apiServiceSpy.getTopicMetrics).toHaveBeenCalledWith(topic.name, false);
  });

  it('only loads the consumer groups once the Consumers tab is opened', () => {
    expect(apiServiceSpy.getTopicConsumerGroups).not.toHaveBeenCalled();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#consumers-tab')!.click();
    fixture.detectChanges();
    expect(apiServiceSpy.getTopicConsumerGroups).toHaveBeenCalledWith(topic.name);
  });

  it('exports the listed configurations with sensitive values masked', () => {
    const configs = [
      { name: 'retention.ms', value: '604800000', source: 'DEFAULT_CONFIG', type: 'LONG', sensitive: false, readOnly: false } as any,
      { name: 'ssl.key.password', value: 'secret', source: 'DYNAMIC_TOPIC_CONFIG', type: 'PASSWORD', sensitive: true, readOnly: true } as any
    ];
    const csv = component.configExportContent('csv', configs).split('\r\n');
    expect(csv[0]).toBe('Name,Value,Source,Type,Sensitive,Read only');
    expect(csv[1]).toBe('retention.ms,604800000,DEFAULT_CONFIG,LONG,false,false');
    expect(csv[2]).toBe('ssl.key.password,********,DYNAMIC_TOPIC_CONFIG,PASSWORD,true,true');
  });
});
