import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { ApiService } from '../../services';
import { GenericResponse, SchemaRegistry, Schema } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';


@Component({
  selector: 'app-schema-registry',
  imports: [CommonModule,RouterModule,PaginationComponent],
  templateUrl: './schema-registry.component.html',
  styleUrl: './schema-registry.component.scss'
})
export class SchemaRegistryComponent {

  schemaRegistry!: SchemaRegistry;
  isLoading: boolean = true;
  readonly pageSize = 10;
  page = 1;

  get pagedSubjects(): Schema[] {
    return this.schemaRegistry.subjects.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.apiService.getSchemaSubjects().subscribe((res: GenericResponse<SchemaRegistry>) => {
      this.schemaRegistry = res.data;
      this.isLoading = false;
    });
  }

}
