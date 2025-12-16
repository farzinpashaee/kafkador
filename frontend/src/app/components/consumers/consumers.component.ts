import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { ApiService } from '../../services';
import { GenericResponse, ConsumerGroup } from '../../models';

@Component({
  selector: 'app-consumers',
  imports: [CommonModule,RouterModule,NgxChartsModule],
  templateUrl: './consumers.component.html',
  styleUrl: './consumers.component.scss'
})
export class ConsumersComponent {
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

  consumerGroups!: ConsumerGroup[];
  isLoading: boolean = true;

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.apiService.getConsumerGroups().subscribe((res: GenericResponse<ConsumerGroup[]>) => {
      this.consumerGroups = res.data;
      this.isLoading = false;
    });
  }

}
