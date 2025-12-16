import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { ConsumerGroup } from '../../models/consumer-group';
import { GenericResponse } from '../../models/generic-response';
import { Alert } from '../../models/alert';
import { Cluster } from '../../models/cluster';
import { Topic } from '../../models/topic';
import { Error } from '../../models/error';
import { Connection } from '../../models/connection';
import { ApiService } from '../../services/api.service';
import { CommonService } from '../../services/common.service';
import { DateTimeService } from '../../services/date-time.service';
import { LocalStorageService } from '../../services/local-storage.service';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule,RouterModule,NgxChartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {

  alerts!: Alert[];
  cluster!: Cluster;
  topics!: Topic[];
  activeConnection: Connection | null = null;
  consumerGroups!: ConsumerGroup[];
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  maxWithPadding: number = 0;
  curve = shape.curveMonotoneX;
  data4: {
    name: string;
    series: { name: string; value: number; }[];
  }[] = [];


  constructor(private apiService: ApiService,
    private localStorageService: LocalStorageService,
    private commonService: CommonService,
    private dateTimeService: DateTimeService) {}

  ngOnInit() {
    this.activeConnection = this.localStorageService.getItem<Connection>("activeConnection");
    this.flags.set('getAlertLoading',true);
    this.flags.set('getClusterLoading',true);
    this.flags.set('getTopicLoading',true);
    this.flags.set('getConsumerGroupLoading',true);
    this.flags.set('getBrokerLoading',true);
    this.apiService.getClusterDetails().subscribe((res: GenericResponse<Cluster>) => {
      this.cluster = res.data;
      this.flags.set('getClusterLoading',false);
      this.flags.set('getBrokerLoading',false);
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
    this.apiService.getConsumerGroups().subscribe((res: GenericResponse<ConsumerGroup[]>) => {
      this.consumerGroups = res.data;
      this.flags.set('getConsumerGroupLoading',false);
    });
    this.apiService.getAlerts().subscribe((res: GenericResponse<Alert[]>) => {
      this.alerts = res.data.map(alert => ({
                                    ...alert,
                                    formattedTime: this.dateTimeService.formatDateTimeFromNow(alert.creationDateTime)
                                  }));
      this.flags.set('getAlertLoading',false);
    });

    this.data4 = this.commonService.generateRandomChartData("MPS",12);
    this.maxWithPadding = this.commonService.getMaxWithPadding(this.data4);

  }


  data = [
    { name: 'a1', value: 5000 },
    { name: 'a2', value: 3000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 3450 },
    { name: 'a5', value: 2400 },
   { name: 'a6', value: 3000 },
   { name: 'a7', value: 1250 },
   { name: 'a8', value: 2300 }
  ];

   data2 = [
      { name: 'a1', value: 100 },
      { name: 'a2', value: 5200 },
      { name: 'a3', value: 1000 },
      { name: 'a4', value: 500 },
      { name: 'a5', value: 800 },
      { name: 'a6', value: 1000 },
      { name: 'a7', value: 950 },
      { name: 'a8', value: 2400 }
    ];


  data3 = [
    { name: 'a1', value: 500 },
    { name: 'a2', value: 2000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 2000 },
    { name: 'a5', value: 3100 },
    { name: 'a6', value: 1700 },
    { name: 'a7', value: 200 },
    { name: 'a8', value: 2400 }
  ];

  view = [50, 50];

  cartTopWidgetWhiteScheme: Color = {
    name: 'cartTopWidgetWhiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };

  mainChartPrimarySchema: Color = {
    name: 'mainChartPrimarySchema',
    selectable: true,
    group: ScaleType.Linear,
    domain: ['#444'] // start, end
  };

  formatXAxis(dateString: string): string {
    const date = new Date(dateString);
    if (date.getDate() % 3 === 0) {
      return `Sep ${date.getDate()}`;
    }
    return '';
  }


}
