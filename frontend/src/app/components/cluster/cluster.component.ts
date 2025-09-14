import { Component } from '@angular/core';
import { ApiService } from '../../services/api.service';
import { Cluster } from '../../models/cluster';
import { GenericResponse } from '../../models/generic-response';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-cluster',
  imports: [],
  templateUrl: './cluster.component.html',
  styleUrl: './cluster.component.scss'
})
export class ClusterComponent{

  cluster!: Cluster;

  constructor(private apiService: ApiService) {}

  ngOnInit() {
    this.apiService.getClusterDetails().subscribe((res: GenericResponse<Cluster>) => {
      console.log(res);
      this.cluster = res.data;
    });
  }

}
