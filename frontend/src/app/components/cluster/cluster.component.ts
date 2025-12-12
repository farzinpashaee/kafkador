import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Cluster } from '../../models/cluster';
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
    { name: 'a5', value: 2400 },
   { name: 'a6', value: 3000 },
   { name: 'a7', value: 1250 },
   { name: 'a8', value: 2300 },
   { name: 'a9', value: 3000 },
   { name: 'a10', value: 1250 },
   { name: 'a11', value: 500 },
  { name: 'a12', value: 4200 },
  { name: 'a13', value: 1650 },
  { name: 'a14', value: 1250 },
  { name: 'a15', value: 1780 }
  ];

  cartTopWidgetWhiteScheme: Color = {
    name: 'cartTopWidgetWhiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };

  cluster!: Cluster;
  isLoading: boolean = true;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.apiService.getClusterDetails().subscribe((res: GenericResponse<Cluster>) => {
      this.cluster = res.data;
      this.isLoading = false;
    });
  }

}
