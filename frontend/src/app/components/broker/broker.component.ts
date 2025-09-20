import { Component, PipeTransform  } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule, AsyncPipe, DecimalPipe  } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { startWith, map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { Broker } from '../../models/broker';
import { Config } from '../../models/config';
import { GenericResponse } from '../../models/generic-response';

@Component({
  selector: 'app-broker',
  imports: [CommonModule,RouterModule,ReactiveFormsModule],
  templateUrl: './broker.component.html',
  styleUrl: './broker.component.scss'
})
export class BrokerComponent {

  brokerId!: string;
  broker!: Broker;
  brokerConfig!: Config[];
  isLoading: boolean = true;
  filter = new FormControl('', { nonNullable: true });

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.brokerId = this.route.snapshot.paramMap.get('id')!;
    this.apiService.getBrokerDetails(this.brokerId).subscribe((res: GenericResponse<Broker>) => {
      this.broker = res.data;
      this.brokerConfig = res.data.config;
      this.isLoading = false;
      this.filter.valueChanges.pipe( startWith(''),map((text: string) => this.search(text)))
        .subscribe(filtered => {
          this.brokerConfig = filtered;
        });
    });

  }

  search(text: string): Config[] {
     return this.broker.config.filter((config: Config) => {
    		const term = text.toLowerCase();
    		return ( config.name.toLowerCase().includes(term) );
    	});
  }

}
