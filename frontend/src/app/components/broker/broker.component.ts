import { Component, PipeTransform  } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule, AsyncPipe, DecimalPipe  } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { startWith, map } from 'rxjs/operators';
import { BehaviorSubject, combineLatest, Observable } from 'rxjs';
import { ApiService, DocumentationService, CommonService } from '../../services';
import { Broker, Config, GenericResponse, Error } from '../../models';

@Component({
  selector: 'app-broker',
  imports: [CommonModule,FormsModule,RouterModule,ReactiveFormsModule],
  templateUrl: './broker.component.html',
  styleUrl: './broker.component.scss'
})
export class BrokerComponent {

  brokerId!: string;
  broker!: Broker;
  brokerConfig!: Config[];
  isLoading: boolean = true;
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
    this.brokerId = this.route.snapshot.paramMap.get('id')!;
    this.apiService.getBrokerDetails(this.brokerId).subscribe((res: GenericResponse<Broker>) => {
      this.broker = res.data;
      this.brokerConfig = res.data.config;
      this.isLoading = false;

      combineLatest([
        this.filter.valueChanges.pipe(startWith('')),
        this.filterSensitive$,
        this.filterEditable$
      ])
        .pipe(map(([text]) => this.search(text)))
        .subscribe((filtered: Config[]) => {
          this.brokerConfig = filtered;
        });


    });

  }

  documentationMod(index: number){
      this.documentation = this.documentationService.createDocumentationHtml(
                                 this.brokerConfig[index].documentation,
                                 this.brokerConfig[index].documentationLink
                               );
  }

  editMod(index: number){
    this.selectedEditConfig =  this.brokerConfig[index];
  }

  updateConfig(){
    this.flags.set('updateConfigLoading',true);
    if (!this.selectedEditConfig) {
        console.error('No config selected');
        return;
    }
    this.apiService.updateBrokerConfig(this.brokerId, this.selectedEditConfig).subscribe({
      next: (res: HttpResponse<GenericResponse<Config>>) => {
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
    return this.broker.config.filter((config: Config) => {
      const matchesText = config.name.toLowerCase().includes(term);
      const matchesSensitive = !this.filterSensitive$.value || config.sensitive === true;
      const matchesEditable = !this.filterEditable$.value || config.readOnly === false;
      return matchesText && matchesSensitive && matchesEditable;
    });
  }


}
