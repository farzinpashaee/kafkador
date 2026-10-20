import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { GenericResponse, SchemaVersion, SchemaRegisterRequest, CompatibilityCheckResult, CompatibilityConfig, Error } from '../../models';

/**
 * One row of the side-by-side diff. A line only in A is 'removed', only in B 'added'; a removed line paired with the
 * added line across from it is 'changed'. Line numbers are 1-based and absent on the side that has no line.
 */
export interface DiffRow {
  left?: string;
  right?: string;
  leftNumber?: number;
  rightNumber?: number;
  kind: 'same' | 'removed' | 'added' | 'changed';
}

/** Past this many line pairs the LCS table gets too big to build in the browser; the diff falls back to positional. */
const MAX_DIFF_CELLS = 4_000_000;

/**
 * Side-by-side line diff of a and b by longest common subsequence, so an inserted line shows as one added row rather
 * than shifting every line after it. Removed and added lines next to each other are paired up as changed rows.
 */
export function diffLines(a: string, b: string): DiffRow[] {
  const left = a.split('\n');
  const right = b.split('\n');
  const n = left.length;
  const m = right.length;

  // Steps that turn a into b: lines kept, removed from a, or added from b.
  const steps: { op: 'same' | 'removed' | 'added'; i: number; j: number }[] = [];
  if (n * m > MAX_DIFF_CELLS) {
    for (let k = 0; k < Math.max(n, m); k++) {
      if (k < n && k < m && left[k] === right[k]) steps.push({ op: 'same', i: k, j: k });
      else {
        if (k < n) steps.push({ op: 'removed', i: k, j: -1 });
        if (k < m) steps.push({ op: 'added', i: -1, j: k });
      }
    }
  } else {
    // lcs[i][j]: length of the longest common subsequence of left[i..] and right[j..].
    const lcs = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        lcs[i][j] = left[i] === right[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
      }
    }
    let i = 0;
    let j = 0;
    while (i < n || j < m) {
      if (i < n && j < m && left[i] === right[j]) steps.push({ op: 'same', i: i++, j: j++ });
      else if (j < m && (i === n || lcs[i][j + 1] >= lcs[i + 1][j])) steps.push({ op: 'added', i: -1, j: j++ });
      else steps.push({ op: 'removed', i: i++, j: -1 });
    }
  }

  const rows: DiffRow[] = [];
  let k = 0;
  while (k < steps.length) {
    if (steps[k].op === 'same') {
      const { i, j } = steps[k++];
      rows.push({ left: left[i], right: right[j], leftNumber: i + 1, rightNumber: j + 1, kind: 'same' });
      continue;
    }
    // A run of removals and additions: line them up side by side.
    const removed: number[] = [];
    const added: number[] = [];
    while (k < steps.length && steps[k].op !== 'same') {
      if (steps[k].op === 'removed') removed.push(steps[k].i);
      else added.push(steps[k].j);
      k++;
    }
    for (let r = 0; r < Math.max(removed.length, added.length); r++) {
      const i = removed[r];
      const j = added[r];
      rows.push({
        left: i === undefined ? undefined : left[i],
        right: j === undefined ? undefined : right[j],
        leftNumber: i === undefined ? undefined : i + 1,
        rightNumber: j === undefined ? undefined : j + 1,
        kind: i === undefined ? 'added' : j === undefined ? 'removed' : 'changed'
      });
    }
  }
  return rows;
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
  /** Versions whose schema is shown indented rather than as registered. */
  formattedVersions = new Set<number>();

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
            // Registry stores JSON minified on one line; indent both so the diff can point at the lines that changed.
            const text = (detail?: SchemaVersion) => detail ? this.formattedSchema(detail) ?? detail.schema : '';
            this.diffRows = diffLines(text(leftRes.body?.data), text(rightRes.body?.data));
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

  get diffAdded(): number {
    return this.diffRows.filter(row => row.right !== undefined && row.kind !== 'same').length;
  }

  get diffRemoved(): number {
    return this.diffRows.filter(row => row.left !== undefined && row.kind !== 'same').length;
  }

  /** Indents the schema being registered; says why when it isn't valid JSON. */
  formatNewSchema() {
    try {
      this.newSchema = JSON.stringify(JSON.parse(this.newSchema), null, 2);
      this.errors.delete('register');
    } catch (e) {
      this.errors.set('register', { code: '400', message: `The schema isn't valid JSON: ${e instanceof SyntaxError ? e.message : e}`, datetime: '' });
    }
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

  /** The schema indented for reading, or null when it isn't JSON (Protobuf, or text that doesn't parse). */
  formattedSchema(detail: SchemaVersion): string | null {
    if (detail.schemaType === 'PROTOBUF') return null;
    try {
      return JSON.stringify(JSON.parse(detail.schema), null, 2);
    } catch {
      return null;
    }
  }

  /** Switches a version's schema between as registered and indented; opens it first if it's closed. */
  toggleFormatted(version: number) {
    if (this.formattedVersions.has(version)) this.formattedVersions.delete(version);
    else this.formattedVersions.add(version);
    if (this.expandedVersion !== version) this.toggleVersion(version);
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
