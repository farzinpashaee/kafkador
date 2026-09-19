import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { GenericResponse, SchemaRegistry, Schema, SchemaRegisterRequest, SchemaLookupResult, Error } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';


@Component({
  selector: 'app-schema-registry',
  imports: [CommonModule,RouterModule,FormsModule,PaginationComponent],
  templateUrl: './schema-registry.component.html',
  styleUrl: './schema-registry.component.scss'
})
export class SchemaRegistryComponent {

  schemaRegistry!: SchemaRegistry;
  isLoading: boolean = true;
  readonly pageSize = 10;
  page = 1;

  newSubjectName = '';
  newSchemaType = 'AVRO';
  newSchema = '';

  deletedSubjectName = '';
  deletePermanent = false;

  lookupId: number | null = null;
  lookupResult: SchemaLookupResult | null = null;

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  get pagedSubjects(): Schema[] {
    return this.schemaRegistry.subjects.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private router: Router,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.getSubjects();
  }

  getSubjects() {
    this.isLoading = true;
    this.apiService.getSchemaSubjects().subscribe((res: GenericResponse<SchemaRegistry>) => {
      this.schemaRegistry = res.data;
      this.page = Math.min(this.page, Math.max(1, Math.ceil((this.schemaRegistry.subjects?.length ?? 0) / this.pageSize)));
      this.isLoading = false;
    });
  }

  openCreateDialog() {
    this.errors.delete('createSubject');
    this.newSubjectName = '';
    this.newSchemaType = 'AVRO';
    this.newSchema = '';
  }

  createSubject() {
    if (!this.newSubjectName.trim()) {
      this.errors.set('createSubject', { code: '400', message: 'Subject name is required', datetime: '' });
      return;
    }
    if (!this.newSchema.trim()) {
      this.errors.set('createSubject', { code: '400', message: 'Schema is required', datetime: '' });
      return;
    }
    this.errors.delete('createSubject');
    this.flags.set('createSubjectLoading', true);
    const request: SchemaRegisterRequest = { schema: this.newSchema, schemaType: this.newSchemaType };
    this.apiService.registerSchema(this.newSubjectName, request).subscribe({
      next: () => {
        this.flags.set('createSubjectLoading', false);
        this.commonService.hideModal('createSubjectModal');
        this.router.navigate(['/subject', this.newSubjectName]);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('createSubject', this.commonService.prepareError(res.error?.error, '500', 'Failed to register schema!'));
        this.flags.set('createSubjectLoading', false);
      }
    });
  }

  openDeleteDialog(subject: Schema) {
    this.errors.delete('deleteSubject');
    this.deletedSubjectName = subject.name;
    this.deletePermanent = false;
  }

  deleteSubject() {
    if (!this.deletedSubjectName) return;
    this.flags.set('deleteSubjectLoading', true);
    this.apiService.deleteSchemaSubject(this.deletedSubjectName, this.deletePermanent).subscribe({
      next: () => {
        this.schemaRegistry.subjects = this.schemaRegistry.subjects.filter(s => s.name !== this.deletedSubjectName);
        this.page = Math.min(this.page, Math.max(1, Math.ceil(this.schemaRegistry.subjects.length / this.pageSize)));
        this.flags.set('deleteSubjectLoading', false);
        this.commonService.hideModal('deleteSubjectModal');
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('deleteSubject', this.commonService.prepareError(res.error?.error, '500', 'Failed to delete subject!'));
        this.flags.set('deleteSubjectLoading', false);
      }
    });
  }

  openLookupDialog() {
    this.errors.delete('lookup');
    this.lookupId = null;
    this.lookupResult = null;
  }

  lookupSchema() {
    if (this.lookupId === null) {
      this.errors.set('lookup', { code: '400', message: 'Schema ID is required', datetime: '' });
      return;
    }
    this.errors.delete('lookup');
    this.lookupResult = null;
    this.flags.set('lookupLoading', true);
    this.apiService.lookupSchemaById(this.lookupId).subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaLookupResult>>) => {
        this.lookupResult = res.body?.data ?? null;
        this.flags.set('lookupLoading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('lookup', this.commonService.prepareError(res.error?.error, '500', 'No schema found with that ID.'));
        this.flags.set('lookupLoading', false);
      }
    });
  }

}
