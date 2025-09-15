import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Cluster } from '../../models/cluster';
import { GenericResponse } from '../../models/generic-response';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-cluster',
  imports: [CommonModule,RouterModule],
  templateUrl: './cluster.component.html',
  styleUrl: './cluster.component.scss'
})
export class ClusterComponent{

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
