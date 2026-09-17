import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { combineLatest } from 'rxjs';
import { startWith, map, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ApiService, CommonService, ValidationService } from '../../services';
import { GenericResponse, AclBinding, Error } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';

@Component({
  selector: 'app-access-control',
  imports: [CommonModule,FormsModule,ReactiveFormsModule,PaginationComponent],
  templateUrl: './access-control.component.html',
  styleUrl: './access-control.component.scss'
})
export class AccessControlComponent {

  bindings: AclBinding[] = [];
  filteredBindings: AclBinding[] = [];
  newBinding!: AclBinding;
  deletedBinding!: AclBinding;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  filter = new FormControl('', { nonNullable: true });
  readonly pageSize = 10;
  page = 1;

  get pagedBindings(): AclBinding[] {
    return this.filteredBindings.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private validationService: ValidationService) {}

  ngOnInit() {
    this.newBinding = this.blankBinding();
    this.deletedBinding = this.blankBinding();
    this.flags.set('getAclBindingsLoading',true);

    combineLatest([
      this.filter.valueChanges.pipe(startWith(''), debounceTime(200), distinctUntilChanged())
    ])
      .pipe(map(([text]) => this.search(text)))
      .subscribe((filtered: AclBinding[]) => {
        this.filteredBindings = filtered;
        this.page = 1;
      });

    this.apiService.getAclBindings().subscribe({ next: (res: HttpResponse<GenericResponse<AclBinding[]>>) => {
        this.bindings = res.body?.data ?? [];
        this.filteredBindings = this.search(this.filter.value);
        this.page = 1;
        this.errors.delete('getAclBindings');
        this.flags.set('authorizerNotConfigured', false);
        this.flags.set('getAclBindingsLoading',false);
      },
      error: (res:HttpErrorResponse) => {
        this.flags.set('authorizerNotConfigured', res.status === 428);
        this.errors.set("getAclBindings",this.commonService.prepareError(res.error.error,'500','Failed to get ACL bindings!'));
        this.flags.set('getAclBindingsLoading',false);
      }
    });
  }

  createAclBinding(){
    const errors = this.validationService.validateRequiredFields(
      this.newBinding, ['resourceType', 'resourceName', 'patternType', 'principal', 'host', 'operation', 'permissionType']);
    if (errors.length > 0) {
      this.errors.set("createAclBinding",{code:'400',message:errors[0],datetime:''});
      return;
    } else {
      this.errors.delete('createAclBinding');
    }
    this.flags.set('createAclBindingLoading',true);
    const bindingToCreate = this.newBinding;
    this.apiService.createAclBinding(bindingToCreate).subscribe({
      next: (res: HttpResponse<GenericResponse<AclBinding>>) => {
        this.bindings = [...this.bindings, res.body?.data ?? bindingToCreate];
        this.filteredBindings = this.search(this.filter.value);
        this.newBinding = this.blankBinding();
        this.flags.set('createAclBindingLoading',false);
        this.commonService.hideModal('createAclBindingModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("createAclBinding",this.commonService.prepareError(res.error.error,'500','Failed to create ACL binding!'));
        this.flags.set('createAclBindingLoading',false);
      }
    });
  }

  openDeleteDialog(binding: AclBinding) {
    this.errors.delete('deleteAclBinding');
    this.deletedBinding = binding;
  }

  deleteAclBinding(){
    if (!this.deletedBinding) return;
    this.flags.set('deleteAclBindingLoading',true);
    this.apiService.deleteAclBinding(this.deletedBinding).subscribe({
        next: () => {
          this.bindings = this.bindings.filter(b => !this.isSameBinding(b, this.deletedBinding));
          this.filteredBindings = this.search(this.filter.value);
          this.page = Math.min(this.page, Math.max(1, Math.ceil(this.filteredBindings.length / this.pageSize)));
          this.flags.set('deleteAclBindingLoading',false);
          this.commonService.hideModal('deleteAclBindingModal');
        },
        error: (res:HttpErrorResponse) => {
          this.errors.set("deleteAclBinding",this.commonService.prepareError(res.error.error,'500','Failed to delete ACL binding!'));
          this.flags.set('deleteAclBindingLoading',false);
        }
      });
  }

  search(text: string): AclBinding[] {
    const term = text.toLowerCase();
    return this.bindings.filter((binding: AclBinding) =>
      binding.resourceName.toLowerCase().includes(term) || binding.principal.toLowerCase().includes(term));
  }

  private isSameBinding(a: AclBinding, b: AclBinding): boolean {
    return a.resourceType === b.resourceType
      && a.resourceName === b.resourceName
      && a.patternType === b.patternType
      && a.principal === b.principal
      && a.host === b.host
      && a.operation === b.operation
      && a.permissionType === b.permissionType;
  }

  private blankBinding(): AclBinding {
    return { resourceType: 'TOPIC', resourceName: '', patternType: 'LITERAL', principal: '', host: '*', operation: 'READ', permissionType: 'ALLOW' };
  }

}
