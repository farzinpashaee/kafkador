import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { GenericResponse } from '../../models/generic-response';

@Component({
  selector: 'app-schema-registry',
  imports: [CommonModule,RouterModule],
  templateUrl: './schema-registry.component.html',
  styleUrl: './schema-registry.component.scss'
})
export class SchemaRegistryComponent {

  subjects!: string[];
  isLoading: boolean = true;

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.apiService.getSchemaSubjects().subscribe((res: GenericResponse<string[]>) => {
      this.subjects = res.data;
      this.isLoading = false;
    });
  }

}
