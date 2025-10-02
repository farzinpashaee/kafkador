import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule,NgxChartsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  data = [
    { name: 'a1', value: 5000 },
    { name: 'a2', value: 3000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 3450 },
    { name: 'a5', value: 2400 }
  ];

   data2 = [
      { name: 'a1', value: 100 },
      { name: 'a2', value: 5200 },
      { name: 'a3', value: 1000 },
      { name: 'a4', value: 1250 },
      { name: 'a5', value: 2400 }
    ];


  data3 = [
    { name: 'a1', value: 500 },
    { name: 'a2', value: 2000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 200 },
    { name: 'a5', value: 2400 }
  ];


  view = [50, 50];

  customColorScheme: Color = {
    name: 'whiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };
}
