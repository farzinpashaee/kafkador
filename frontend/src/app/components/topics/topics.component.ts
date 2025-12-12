import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { GenericResponse } from '../../models/generic-response';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { Topic } from '../../models/topic';

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

  topics!: Topic[];
  isLoading: boolean = true;

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.apiService.getTopics().subscribe((res: GenericResponse<Topic[]>) => {
      this.topics = res.data;
      this.isLoading = false;
    });
  }

}
