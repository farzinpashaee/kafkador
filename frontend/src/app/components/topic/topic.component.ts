import { ChangeDetectorRef, Component, OnDestroy, PipeTransform  } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule, AsyncPipe, DecimalPipe  } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { startWith, map } from 'rxjs/operators';
import { BehaviorSubject, combineLatest, Observable, Subscription } from 'rxjs';
import { ApiService, DocumentationService, CommonService} from '../../services';
import { Broker, GenericResponse, Config, Topic, Error } from '../../models';

const MAX_VISIBLE_TEST_MESSAGES = 200;

@Component({
  selector: 'app-topic',
  imports: [CommonModule,FormsModule,RouterModule,ReactiveFormsModule],
  templateUrl: './topic.component.html',
  styleUrl: './topic.component.scss'
})
export class TopicComponent implements OnDestroy {

  topicName!: string;
  topic!: Topic;
  topicConfig!: Config[];
  isLoading: boolean = true;
  documentation!: string;
  selectedEditConfig?: Config;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  filter = new FormControl('', { nonNullable: true });
  filterSensitive$ = new BehaviorSubject<boolean>(false);
  filterEditable$ = new BehaviorSubject<boolean>(false);

  listening: boolean = false;
  activeGroupId: string = '';
  consumedMessages: string[] = [];
  producerEvent: string = '';
  private testTopicSubscription?: Subscription;

  constructor(private apiService: ApiService,
    private documentationService: DocumentationService,
    private commonService: CommonService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.topicName = this.route.snapshot.paramMap.get('name')!;
    this.apiService.getTopicDetails(this.topicName).subscribe((res: GenericResponse<Topic>) => {
      this.topic = res.data;
      this.topicConfig = res.data.config;
      this.isLoading = false;

      combineLatest([
        this.filter.valueChanges.pipe(startWith('')),
        this.filterSensitive$,
        this.filterEditable$
      ])
        .pipe(map(([text]) => this.search(text)))
        .subscribe((filtered: Config[]) => {
          this.topicConfig = filtered;
        });

      if (this.route.snapshot.queryParamMap.get('listen') === 'true') {
        const groupId = this.route.snapshot.queryParamMap.get('groupId') ?? undefined;
        this.cdr.detectChanges();
        this.commonService.showTab('test-topic-tab');
        this.startListening(groupId);
      }
    });
  }

  documentationMod(config: Config){
      this.documentation = this.documentationService.createDocumentationHtml(
                                 config.documentation,
                                 config.documentationLink
                               );
  }

  editMod(config: Config){
    this.selectedEditConfig = config;
  }

  updateConfig(){
    this.flags.set('updateConfigLoading',true);
    if (!this.selectedEditConfig) {
        console.error('No config selected');
        return;
    }
    this.apiService.updateTopicConfig(this.topic.name, this.selectedEditConfig).subscribe({
      next: (res: HttpResponse<void>) => {
        this.flags.set('updateConfigLoading',false);
        this.commonService.hideModal('editModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("updateConfig",this.commonService.prepareError(res.error.error,'500','Failed to update configuration!'));
        this.flags.set('updateConfigLoading',false)
      }
    });

  }

  search(text: string): Config[] {
    const term = text.toLowerCase();
    return this.topic.config.filter((config: Config) => {
      const matchesText = config.name.toLowerCase().includes(term);
      const matchesSensitive = !this.filterSensitive$.value || config.sensitive === true;
      const matchesEditable = !this.filterEditable$.value || config.readOnly === false;
      return matchesText && matchesSensitive && matchesEditable;
    });
  }

  getConfigValue(key: String): string | undefined {
    const matchedItem = this.topicConfig.find((item: Config) => item.name === key);
    return matchedItem ? matchedItem.value : undefined;
  }

  ngOnDestroy(): void {
    this.testTopicSubscription?.unsubscribe();
  }

  toggleListening(): void {
    if (this.listening) {
      this.stopListening();
    } else {
      this.startListening();
    }
  }

  startListening(groupId?: string): void {
    this.errors.delete('testTopicConsumer');
    this.listening = true;
    this.activeGroupId = groupId || `kafkador-test-${Date.now()}`;
    this.testTopicSubscription = this.apiService.consumeTopicMessages(this.topicName, this.activeGroupId).subscribe({
      next: (message: string) => {
        this.consumedMessages = [message, ...this.consumedMessages].slice(0, MAX_VISIBLE_TEST_MESSAGES);
      },
      error: (err) => {
        this.listening = false;
        this.errors.set('testTopicConsumer', this.commonService.prepareError(undefined, '500', err?.message ?? 'Connection to the message stream was lost.'));
      }
    });
  }

  stopListening(): void {
    this.testTopicSubscription?.unsubscribe();
    this.listening = false;
  }

  sendEvent(): void {
    if (!this.producerEvent.trim()) {
      return;
    }
    this.errors.delete('sendEvent');
    this.flags.set('sendingEvent', true);
    this.apiService.produceTopicMessage(this.topicName, { value: this.producerEvent }).subscribe({
      next: () => {
        this.flags.set('sendingEvent', false);
        this.producerEvent = '';
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('sendEvent', this.commonService.prepareError(res.error?.error, '500', 'Failed to send event!'));
        this.flags.set('sendingEvent', false);
      }
    });
  }

}
