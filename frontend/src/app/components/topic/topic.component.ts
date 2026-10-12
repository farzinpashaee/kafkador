import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { startWith, map } from 'rxjs/operators';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { CONFIG_EXPORT_FIELDS } from '../../services/config-export';
import { EXPORT_FORMATS, ExportFormat, buildExport, downloadExport } from '../../services/table-export';
import { ApiService, DocumentationService, CommonService} from '../../services';
import { GenericResponse, Config, Topic, Error } from '../../models';
import { TopicRecordsComponent } from '../topic-records/topic-records.component';
import { TopicConsumersComponent } from '../topic-consumers/topic-consumers.component';

@Component({
  selector: 'app-topic',
  imports: [CommonModule,FormsModule,RouterModule,ReactiveFormsModule,TopicRecordsComponent,TopicConsumersComponent],
  templateUrl: './topic.component.html',
  styleUrl: './topic.component.scss'
})
export class TopicComponent {

  topicName!: string;
  topic!: Topic;
  topicConfig!: Config[];
  readonly exportFormats = EXPORT_FORMATS;
  isLoading: boolean = true;
  // The Records and Consumers tabs are created on first open, so viewing a topic doesn't query them until asked.
  recordsOpened = false;
  consumersOpened = false;
  documentation!: string;
  selectedEditConfig?: Config;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  filter = new FormControl('', { nonNullable: true });
  filterSensitive$ = new BehaviorSubject<boolean>(false);
  filterEditable$ = new BehaviorSubject<boolean>(false);

  constructor(private apiService: ApiService,
    private documentationService: DocumentationService,
    private commonService: CommonService,
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

  /** Exports the configurations currently listed (search and filters applied). */
  exportConfigs(format: ExportFormat): void {
    downloadExport(this.configExportContent(format, this.topicConfig ?? []), format, `topic-${this.topicName}-configs`);
  }

  configExportContent(format: ExportFormat, configs: Config[]): string {
    return buildExport(format, CONFIG_EXPORT_FIELDS, configs, 'configs', 'config');
  }

  getConfigValue(key: String): string | undefined {
    const matchedItem = this.topicConfig.find((item: Config) => item.name === key);
    return matchedItem ? matchedItem.value : undefined;
  }

}
