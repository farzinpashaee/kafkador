import { Component, PipeTransform  } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule, AsyncPipe, DecimalPipe  } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { startWith, map } from 'rxjs/operators';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { ApiService, DocumentationService } from '../../services';
import { Broker, GenericResponse, Config, Topic } from '../../models';



@Component({
  selector: 'app-topic',
  imports: [CommonModule,FormsModule,RouterModule,ReactiveFormsModule],
  templateUrl: './topic.component.html',
  styleUrl: './topic.component.scss'
})
export class TopicComponent {

  topicName!: string;
  topic!: Topic;
  topicConfig!: Config[];
  isLoading: boolean = true;
  documentation!: string;

  filter = new FormControl('', { nonNullable: true });
  filterSensitive$ = new BehaviorSubject<boolean>(false);
  filterEditable$ = new BehaviorSubject<boolean>(false);

  constructor(private apiService: ApiService,
    private documentationService: DocumentationService,
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

    });
  }

  documentationMod(index: number){
      this.documentation = this.documentationService.createDocumentationHtml(
                                 this.topicConfig[index].documentation,
                                 this.topicConfig[index].documentationLink
                               );
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

}
