import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../services/api.service';
import { CommonService } from '../../services/common.service';
import { Cluster } from '../../models/cluster';
import { Chart } from '../../models/chart';
import { Error } from '../../models/error';
import { GenericResponse } from '../../models/generic-response';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-cluster',
  imports: [CommonModule,RouterModule,NgxChartsModule],
  templateUrl: './cluster.component.html',
  styleUrl: './cluster.component.scss'
})
export class ClusterComponent{

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

  cluster!: Cluster;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private commonService: CommonService,
    private apiService: ApiService) {}

  ngOnInit() {
    this.flags.set('getClusterLoading',true);
    this.flags.set('agentEnabled',true);
    this.apiService.getClusterDetails().subscribe((res: GenericResponse<Cluster>) => {
      this.cluster = res.data;
      this.flags.set('getClusterLoading',false);
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
