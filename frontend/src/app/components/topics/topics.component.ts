import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { CommonService } from '../../services/common.service';
import { GenericResponse } from '../../models/generic-response';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { Topic } from '../../models/topic';
import { Chart } from '../../models/chart';
import { Error } from '../../models/error';

@Component({
  selector: 'app-topics',
  imports: [CommonModule,RouterModule,NgxChartsModule],
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

  topics!: Topic[];
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.flags.set('getTopicLoading',true);
    this.flags.set('agentEnabled',true);
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

}
