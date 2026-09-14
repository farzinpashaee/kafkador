import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { ApiService, CommonService, ValidationService } from '../../services';
import { GenericResponse, Topic, Chart, Error } from '../../models';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { combineLatest } from 'rxjs';
import { startWith, map, debounceTime, distinctUntilChanged } from 'rxjs/operators';

@Component({
  selector: 'app-topics',
  imports: [CommonModule,RouterModule,NgxChartsModule,FormsModule,ReactiveFormsModule],
  templateUrl: './topics.component.html',
  styleUrl: './topics.component.scss'
})
export class TopicsComponent {

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

  topics: Topic[] = [];
  filteredTopics: Topic[] = [];
  newTopic!: Topic;
  deletedTopic!: Topic;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  filter = new FormControl('', { nonNullable: true });

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.newTopic = this.blankTopic();
    this.deletedTopic = this.blankTopic();
    this.flags.set('getTopicLoading',true);
    this.flags.set('agentEnabled',true);

    combineLatest([
      this.filter.valueChanges.pipe(startWith(''), debounceTime(200), distinctUntilChanged())
    ])
      .pipe(map(([text]) => this.search(text)))
      .subscribe((filtered: Topic[]) => {
        this.filteredTopics = filtered;
      });

    this.apiService.getTopics().subscribe({ next: (res: HttpResponse<GenericResponse<Topic[]>>) => {
        this.topics = res.body?.data ?? [];
        this.filteredTopics = this.search(this.filter.value);
        this.errors.delete('getTopics');
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

  createTopic(){
    const errors = [
      ...this.validationService.validateRequiredFields(this.newTopic, ['name', 'partitions', 'replicatorFactor']),
      ...this.validationService.validatePositiveIntegerFields(this.newTopic, ['partitions', 'replicatorFactor'])
    ];
    if (errors.length > 0) {
      this.errors.set("createTopic",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('createTopic');
    }
    this.flags.set('createTopicLoading',true);
    const topicToCreate = this.newTopic;
    this.apiService.createTopic(topicToCreate).subscribe({
      next: (res: HttpResponse<GenericResponse<Topic>>) => {
        this.topics = [...this.topics, res.body?.data ?? topicToCreate];
        this.filteredTopics = this.search(this.filter.value);
        this.newTopic = this.blankTopic();
        this.flags.set('createTopicLoading',false);
        this.commonService.hideModal('createTopicModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("createTopic",this.commonService.prepareError(res.error.error,'500','Failed to add new Topic!'));
        this.flags.set('createTopicLoading',false);
      }
    });
  }

  openDeleteDialog(topic: Topic) {
    this.errors.delete('deleteTopic');
    this.deletedTopic = topic;
  }

  deleteTopic(){
    if (!this.deletedTopic) return;
    this.flags.set('deleteTopicLoading',true);
    this.apiService.deleteTopic(this.deletedTopic.name).subscribe({
        next: () => {
          this.topics = this.topics.filter(c => c.name !== this.deletedTopic.name);
          this.filteredTopics = this.search(this.filter.value);
          this.flags.set('deleteTopicLoading',false);
          this.commonService.hideModal('deleteTopicModal');
        },
        error: (res:HttpErrorResponse) => {
          this.errors.set("deleteTopic",this.commonService.prepareError(res.error.error,'500','Failed to delete topic!'));
          this.flags.set('deleteTopicLoading',false);
        }
      });
  }

  search(text: string): Topic[] {
    const term = text.toLowerCase();
    return this.topics.filter((topic: Topic) => topic.name.toLowerCase().includes(term));
  }

  private blankTopic(): Topic {
    return { name: '' , id : '' , partitions : 1 , internal: false , replicatorFactor: 1, config: [] };
  }

}
