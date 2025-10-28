import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Alert } from '../../models/alert';
import { GenericResponse } from '../../models/generic-response';
import { ApiService } from '../../services/api.service';
import { DateTimeService } from '../../services/date-time.service';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule,NgxChartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {

  alerts!: Alert[];
  alertsLoading: boolean = true;

  constructor(private apiService: ApiService,
    private dateTimeService: DateTimeService) {}

  ngOnInit() {
    this.apiService.getAlerts().subscribe((res: GenericResponse<Alert[]>) => {
      this.alerts = res.data.map(alert => ({
                                    ...alert,
                                    formattedTime: this.dateTimeService.formatDateTimeFromNow(alert.creationDateTime)
                                  }));
      this.alertsLoading = false;
    });
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

 data4 = [
    {
      "name": "Series A",
      "series": [
   { name: 'Sep 1', value: 500 },
    { name: 'Sep 2', value: 620 },
    { name: 'Sep 3', value: 800 },
    { name: 'Sep 4', value: 1200 },
    { name: 'Sep 5', value: 2700 },
    { name: 'Sep 6', value: 1700 },
    { name: 'Sep 7', value: 950 },
    { name: 'Sep 8', value: 950 },
    { name: 'Sep 9', value: 1250 },
    { name: 'Sep 10', value: 1350 },
    { name: 'Sep 11', value: 1950 },
    { name: 'Sep 12', value: 2950 },
    { name: 'Sep 13', value: 1650 },
    { name: 'Sep 14', value: 1900 },
    { name: 'Sep 15', value: 2150 },
    { name: 'Sep 16', value: 1650 },
    { name: 'Sep 17', value: 1500 },
    { name: 'Sep 18', value: 1260 },
    { name: 'Sep 19', value: 1400 },
    { name: 'Sep 20', value: 1300 }
      ]
    }
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
    group: ScaleType.Ordinal,
    domain: ['#00D3D3']
  };

  formatXAxis(dateString: string): string {
    const date = new Date(dateString);
    if (date.getDate() % 3 === 0) {
      return `Sep ${date.getDate()}`;
    }
    return '';
  }


}
