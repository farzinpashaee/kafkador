import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ApiService, CommonService, LocalStorageService } from '../../services';
import { ConsumerGroup, Chart, Error, Connection, GenericResponse, Topic } from '../../models';


@Component({
  selector: 'app-consumers',
  imports: [CommonModule,RouterModule,NgxChartsModule,FormsModule],
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

  topicName = '';
  topics!: Topic[];
  suggestedTopics!: Topic[];
  consumerGroups!: ConsumerGroup[];
  activeConnection: Connection | null = null;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private localStorageService: LocalStorageService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.activeConnection = this.localStorageService.getItem<Connection>("activeConnection");
    this.flags.set('getConsumerGroupsLoading',true);
    this.flags.set('agentEnabled',true);
    this.apiService.getConsumerGroups().subscribe({ next: (res: HttpResponse<GenericResponse<ConsumerGroup[]>>) => {
        this.consumerGroups = res.body?.data ?? [];
        this.flags.set('getConsumerGroupsLoading',false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("getConsumerGroups",this.commonService.prepareError(res.error.error,'500','Failed to get topics!'));
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

  searchTopic(term: string): Topic[] {
    const lower = term.toLowerCase();
    return this.topics.filter(i => i.name.toLowerCase().includes(lower));
  }

  selectTopic(topic: Topic): void {
    this.topicName = topic.name;
    this.flags.set('showDropdown',false);
  }

  onInputChange(): void {
    if (this.topicName.length < 2) {
      this.suggestedTopics = [];
      this.flags.set('showDropdown',false);
      return;
    }
    this.suggestedTopics = this.searchTopic(this.topicName);
    this.flags.set('showDropdown',this.suggestedTopics.length > 0);
  }

}
