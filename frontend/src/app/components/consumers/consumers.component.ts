import { Component } from '@angular/core';
import { ActivatedRoute,Router,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { combineLatest } from 'rxjs';
import { startWith, map, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ApiService, CommonService, ValidationService, LocalStorageService } from '../../services';
import { ConsumerGroup, Chart, Error, Connection, GenericResponse, Topic } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';


@Component({
  selector: 'app-consumers',
  imports: [CommonModule,RouterModule,NgxChartsModule,FormsModule,ReactiveFormsModule,PaginationComponent],
  templateUrl: './consumers.component.html',
  styleUrl: './consumers.component.scss'
})
export class ConsumersComponent {
  data = [
    { name: 'a1', value: 5000 },
    { name: 'a2', value: 3000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 3450 },
    { name: 'a5', value: 2400 }
  ];

  cartTopWidgetWhiteScheme: Color = {
    name: 'cartTopWidgetWhiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };

  newConsumerGroupId = '';
  topicName = '';
  listenGroupId = '';
  listenTopicName = '';
  topics: Topic[] = [];
  consumerGroups: ConsumerGroup[] = [];
  filteredConsumerGroups: ConsumerGroup[] = [];
  activeConnection: Connection | null = null;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  filter = new FormControl('', { nonNullable: true });
  readonly pageSize = 10;
  page = 1;

  get pagedConsumerGroups(): ConsumerGroup[] {
    return this.filteredConsumerGroups.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService,
    private localStorageService: LocalStorageService,
    private router: Router,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.activeConnection = this.localStorageService.getItem<Connection>("activeConnection");
    this.flags.set('getConsumerGroupsLoading',true);
    this.flags.set('agentEnabled',true);

    combineLatest([
      this.filter.valueChanges.pipe(startWith(''), debounceTime(200), distinctUntilChanged())
    ])
      .pipe(map(([text]) => this.search(text)))
      .subscribe((filtered: ConsumerGroup[]) => {
        this.filteredConsumerGroups = filtered;
        this.page = 1;
      });

    this.apiService.getConsumerGroups().subscribe({ next: (res: HttpResponse<GenericResponse<ConsumerGroup[]>>) => {
        this.consumerGroups = res.body?.data ?? [];
        this.filteredConsumerGroups = this.search(this.filter.value);
        this.page = 1;
        this.errors.delete('getConsumerGroups');
        this.flags.set('getConsumerGroupsLoading',false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("getConsumerGroups",this.commonService.prepareError(res.error.error,'500','Failed to get consumer groups!'));
        this.flags.set('getConsumerGroupsLoading',false);
      }
    });

    this.apiService.getTopics().subscribe({ next: (res: HttpResponse<GenericResponse<Topic[]>>) => {
          this.topics = res.body?.data ?? [];
          this.flags.set('getTopicLoading',false);
        },
        error: (res:HttpErrorResponse) => {
          this.errors.set("getTopics",this.commonService.prepareError(res.error.error,'500','Failed to get topics!'));
          this.flags.set('getTopicLoading',false);
        }
    });

    this.apiService.getChart('x','q').subscribe({ next: (res: HttpResponse<GenericResponse<Chart>>) => {
          // TODO: get chart data
          },
          error: (res:HttpErrorResponse) => {
            if(res.status==428){
              this.flags.set('agentEnabled',false);
            } else {
              this.errors.set("getChart",this.commonService.prepareError(res.error.error,'500','Failed to get cluster chart information!'));
              this.flags.set('getChartLoading',false);
            }
          }
        });
  }

  search(text: string): ConsumerGroup[] {
    const term = text.toLowerCase();
    return this.consumerGroups.filter((group: ConsumerGroup) => group.id.toLowerCase().includes(term));
  }

  useInstanceConsumerId(): void {
    this.apiService.getDefaultConsumerGroupId().subscribe({
      next: (res: HttpResponse<GenericResponse<string>>) => {
        if (res.body?.data) this.newConsumerGroupId = res.body.data;
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("createConsumerGroup",this.commonService.prepareError(res.error.error,'500','Failed to get instance consumer ID!'));
      }
    });
  }

  createAndListen(): void {
    const errors = this.validationService.validateRequiredFields(
      { id: this.newConsumerGroupId, topic: this.topicName }, ['id', 'topic']);
    if (errors.length > 0) {
      this.errors.set("createConsumerGroup",{code:'400',message:errors[0],datetime:''});
      return;
    }
    this.errors.delete('createConsumerGroup');
    this.commonService.hideModal('createConsumerGroupModal');
    this.router.navigate(['/topic', this.topicName], {
      queryParams: { groupId: this.newConsumerGroupId, listen: 'true' },
      fragment: 'testTopic'
    });
  }

  openListenDialog(consumerGroup: ConsumerGroup): void {
    this.errors.delete('listenConsumerGroup');
    this.listenGroupId = consumerGroup.id;
    this.listenTopicName = '';
  }

  listenToExistingGroup(): void {
    const errors = this.validationService.validateRequiredFields(
      { topic: this.listenTopicName }, ['topic']);
    if (errors.length > 0) {
      this.errors.set("listenConsumerGroup",{code:'400',message:errors[0],datetime:''});
      return;
    }
    this.errors.delete('listenConsumerGroup');
    this.commonService.hideModal('listenConsumerGroupModal');
    this.router.navigate(['/topic', this.listenTopicName], {
      queryParams: { groupId: this.listenGroupId, listen: 'true' },
      fragment: 'testTopic'
    });
  }

}
