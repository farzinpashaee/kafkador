import { Component } from '@angular/core';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { GenericResponse } from '../../models/generic-response';
import { Topic } from '../../models/topic';

@Component({
  selector: 'app-topics',
  imports: [CommonModule,RouterModule],
  templateUrl: './topics.component.html',
  styleUrl: './topics.component.scss'
})
export class TopicsComponent {

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
