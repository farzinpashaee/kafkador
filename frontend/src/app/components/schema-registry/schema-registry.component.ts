import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute,RouterModule } from '@angular/router';
import { ApiService } from '../../services';
import { GenericResponse, SchemaRegistry } from '../../models';


@Component({
  selector: 'app-schema-registry',
  imports: [CommonModule,RouterModule],
  templateUrl: './schema-registry.component.html',
  styleUrl: './schema-registry.component.scss'
})
export class SchemaRegistryComponent {

  schemaRegistry!: SchemaRegistry;
  isLoading: boolean = true;

  constructor(private apiService: ApiService,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.apiService.getSchemaSubjects().subscribe((res: GenericResponse<SchemaRegistry>) => {
      this.schemaRegistry = res.data;
      this.isLoading = false;
    });
  }

}
