import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { GenericResponse, SchemaVersion, SchemaRegisterRequest, CompatibilityCheckResult, CompatibilityConfig, Error } from '../../models';

interface DiffRow {
  left: string;
  right: string;
  changed: boolean;
}

const COMPATIBILITY_LEVELS = ['BACKWARD', 'BACKWARD_TRANSITIVE', 'FORWARD', 'FORWARD_TRANSITIVE', 'FULL', 'FULL_TRANSITIVE', 'NONE'];

@Component({
  selector: 'app-subject',
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './subject.component.html',
  styleUrl: './subject.component.scss'
})
export class SubjectComponent implements OnInit {

  readonly compatibilityLevels = COMPATIBILITY_LEVELS;

  subjectName!: string;
  versions: number[] = [];
  versionDetails: Map<number, SchemaVersion> = new Map();
  expandedVersion: number | null = null;

  compareLeftVersion: number | null = null;
  compareRightVersion: number | null = null;
  diffRows: DiffRow[] = [];

  newSchema = '';
  newSchemaType = 'AVRO';
  compatibilityResult: CompatibilityCheckResult | null = null;
  registeredVersion: SchemaVersion | null = null;

  globalCompatibility: CompatibilityConfig = {};
  subjectCompatibility: CompatibilityConfig = {};
  selectedCompatibilityLevel = '';

  deletePermanent = false;

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private route: ActivatedRoute,
    private router: Router) {}

  ngOnInit() {
    this.subjectName = this.route.snapshot.paramMap.get('name')!;
    this.loadVersions();
    this.loadCompatibility();
  }

  loadVersions() {
    this.errors.delete('getVersions');
    this.flags.set('versionsLoading', true);
    this.apiService.getSchemaVersions(this.subjectName).subscribe({
      next: (res: HttpResponse<GenericResponse<number[]>>) => {
        this.versions = (res.body?.data ?? []).slice().sort((a, b) => b - a);
        this.flags.set('versionsLoading', false);
        if (this.versions.length > 0) this.toggleVersion(this.versions[0]);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getVersions', this.commonService.prepareError(res.error?.error, '500', 'Failed to load schema versions!'));
        this.flags.set('versionsLoading', false);
      }
    });
  }

  toggleVersion(version: number) {
    if (this.expandedVersion === version) {
      this.expandedVersion = null;
      return;
    }
    this.expandedVersion = version;
    if (this.versionDetails.has(version)) return;
    this.apiService.getSchemaVersion(this.subjectName, String(version)).subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaVersion>>) => {
        if (res.body?.data) this.versionDetails.set(version, res.body.data);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getVersions', this.commonService.prepareError(res.error?.error, '500', 'Failed to load schema version!'));
      }
    });
  }

  showDiff() {
    if (this.compareLeftVersion === null || this.compareRightVersion === null) return;
    this.errors.delete('diff');
    const left$ = this.apiService.getSchemaVersion(this.subjectName, String(this.compareLeftVersion));
    const right$ = this.apiService.getSchemaVersion(this.subjectName, String(this.compareRightVersion));
    this.flags.set('diffLoading', true);
    left$.subscribe({
      next: (leftRes: HttpResponse<GenericResponse<SchemaVersion>>) => {
        right$.subscribe({
          next: (rightRes: HttpResponse<GenericResponse<SchemaVersion>>) => {
            const left = leftRes.body?.data?.schema ?? '';
            const right = rightRes.body?.data?.schema ?? '';
            this.diffRows = this.diffLines(left, right);
            this.flags.set('diffLoading', false);
          },
          error: () => {
            this.errors.set('diff', { code: '500', message: 'Failed to load one of the compared versions.', datetime: '' });
            this.flags.set('diffLoading', false);
          }
        });
      },
      error: () => {
        this.errors.set('diff', { code: '500', message: 'Failed to load one of the compared versions.', datetime: '' });
        this.flags.set('diffLoading', false);
      }
    });
  }

  /**
   * A positional (same-index) line comparison — not a real LCS diff. Good enough to spot
   * what changed between two schema versions without pulling in a diff library; a line
   * inserted/removed in the middle will shift every row after it rather than aligning.
   */
  private diffLines(a: string, b: string): DiffRow[] {
    const linesA = a.split('\n');
    const linesB = b.split('\n');
    const max = Math.max(linesA.length, linesB.length);
    const rows: DiffRow[] = [];
    for (let i = 0; i < max; i++) {
      const left = linesA[i] ?? '';
      const right = linesB[i] ?? '';
      rows.push({ left, right, changed: left !== right });
    }
    return rows;
  }

  checkCompatibility() {
    if (!this.newSchema.trim()) {
      this.errors.set('register', { code: '400', message: 'Schema is required', datetime: '' });
      return;
    }
    this.errors.delete('register');
    this.compatibilityResult = null;
    this.flags.set('checkingCompatibility', true);
    const request: SchemaRegisterRequest = { schema: this.newSchema, schemaType: this.newSchemaType };
    this.apiService.checkSchemaCompatibility(this.subjectName, request).subscribe({
      next: (res: HttpResponse<GenericResponse<CompatibilityCheckResult>>) => {
        this.compatibilityResult = res.body?.data ?? null;
        this.flags.set('checkingCompatibility', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('register', this.commonService.prepareError(res.error?.error, '500', 'Failed to check compatibility!'));
        this.flags.set('checkingCompatibility', false);
      }
    });
  }

  registerSchema() {
    if (!this.newSchema.trim()) {
      this.errors.set('register', { code: '400', message: 'Schema is required', datetime: '' });
      return;
    }
    this.errors.delete('register');
    this.registeredVersion = null;
    this.flags.set('registering', true);
    const request: SchemaRegisterRequest = { schema: this.newSchema, schemaType: this.newSchemaType };
    this.apiService.registerSchema(this.subjectName, request).subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaVersion>>) => {
        this.registeredVersion = res.body?.data ?? null;
        this.flags.set('registering', false);
        this.compatibilityResult = null;
        this.loadVersions();
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('register', this.commonService.prepareError(res.error?.error, '500', 'Failed to register schema!'));
        this.flags.set('registering', false);
      }
    });
  }

  loadCompatibility() {
    this.errors.delete('getCompatibility');
    this.flags.set('compatibilityLoading', true);
    this.apiService.getGlobalCompatibility().subscribe({
      next: (res: HttpResponse<GenericResponse<CompatibilityConfig>>) => {
        this.globalCompatibility = res.body?.data ?? {};
        this.fetchSubjectCompatibility();
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getCompatibility', this.commonService.prepareError(res.error?.error, '500', 'Failed to load compatibility settings!'));
        this.flags.set('compatibilityLoading', false);
      }
    });
  }

  private fetchSubjectCompatibility() {
    this.apiService.getSubjectCompatibility(this.subjectName).subscribe({
      next: (res: HttpResponse<GenericResponse<CompatibilityConfig>>) => {
        this.subjectCompatibility = res.body?.data ?? {};
        this.selectedCompatibilityLevel = this.subjectCompatibility.level || this.globalCompatibility.level || 'BACKWARD';
        this.flags.set('compatibilityLoading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getCompatibility', this.commonService.prepareError(res.error?.error, '500', 'Failed to load compatibility settings!'));
        this.flags.set('compatibilityLoading', false);
      }
    });
  }

  saveSubjectCompatibility() {
    this.errors.delete('saveCompatibility');
    this.flags.set('savingCompatibility', true);
    this.apiService.saveSubjectCompatibility(this.subjectName, this.selectedCompatibilityLevel).subscribe({
      next: (res: HttpResponse<GenericResponse<CompatibilityConfig>>) => {
        this.subjectCompatibility = res.body?.data ?? { level: this.selectedCompatibilityLevel };
        this.flags.set('savingCompatibility', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('saveCompatibility', this.commonService.prepareError(res.error?.error, '500', 'Failed to save compatibility level!'));
        this.flags.set('savingCompatibility', false);
      }
    });
  }

  clearSubjectCompatibility() {
    this.errors.delete('saveCompatibility');
    this.flags.set('savingCompatibility', true);
    this.apiService.clearSubjectCompatibility(this.subjectName).subscribe({
      next: () => {
        this.subjectCompatibility = {};
        this.selectedCompatibilityLevel = this.globalCompatibility.level || 'BACKWARD';
        this.flags.set('savingCompatibility', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('saveCompatibility', this.commonService.prepareError(res.error?.error, '500', 'Failed to clear compatibility override!'));
        this.flags.set('savingCompatibility', false);
      }
    });
  }

  deleteVersion(version: number) {
    this.flags.set('deletingVersion:' + version, true);
    this.apiService.deleteSchemaVersion(this.subjectName, String(version), false).subscribe({
      next: () => {
        this.versions = this.versions.filter(v => v !== version);
        this.versionDetails.delete(version);
        this.flags.set('deletingVersion:' + version, false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getVersions', this.commonService.prepareError(res.error?.error, '500', 'Failed to delete version!'));
        this.flags.set('deletingVersion:' + version, false);
      }
    });
  }

  isDeletingVersion(version: number): boolean {
    return this.flags.get('deletingVersion:' + version) === true;
  }

  openDeleteSubjectDialog() {
    this.errors.delete('deleteSubject');
    this.deletePermanent = false;
  }

  deleteSubject() {
    this.errors.delete('deleteSubject');
    this.flags.set('deletingSubject', true);
    this.apiService.deleteSchemaSubject(this.subjectName, this.deletePermanent).subscribe({
      next: () => {
        this.flags.set('deletingSubject', false);
        this.commonService.hideModal('deleteSubjectModal');
        this.router.navigate(['/schema-registry']);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('deleteSubject', this.commonService.prepareError(res.error?.error, '500', 'Failed to delete subject!'));
        this.flags.set('deletingSubject', false);
      }
    });
  }

}
