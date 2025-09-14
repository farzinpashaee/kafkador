import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Broker } from '../../models/broker';
import { GenericResponse } from '../../models/generic-response';

@Component({
  selector: 'app-broker',
  imports: [CommonModule,RouterModule],
  templateUrl: './broker.component.html',
  styleUrl: './broker.component.scss'
})
export class BrokerComponent {

  brokerId!: string;
  broker!: Broker;
  isLoading: boolean = true;

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.brokerId = this.route.snapshot.paramMap.get('id')!;
    this.apiService.getBrokerDetails(this.brokerId).subscribe((res: GenericResponse<Broker>) => {
      this.broker = res.data;
      this.isLoading = false;
    });
  }

}
