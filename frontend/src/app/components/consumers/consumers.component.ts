import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { GenericResponse } from '../../models/generic-response';
import { ConsumerGroup } from '../../models/consumer-group';

@Component({
  selector: 'app-consumers',
  imports: [CommonModule,RouterModule],
  templateUrl: './consumers.component.html',
  styleUrl: './consumers.component.scss'
})
export class ConsumersComponent {

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
