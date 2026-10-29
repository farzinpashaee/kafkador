import { Component, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, AppInfoService, CommonService } from '../../services';
import { AccessSettings, Error } from '../../models';
import { AccessSettingsFormComponent } from '../access-settings-form/access-settings-form.component';

/**
 * First-run setup, in two steps: the embedded database's credentials, then who may use Kafkador. An instance set up
 * before the second step existed only shows that one.
 */
@Component({
  selector: 'app-setup',
  imports: [CommonModule, FormsModule, AccessSettingsFormComponent],
  templateUrl: './setup.component.html',
  styleUrl: './setup.component.scss'
})
export class SetupComponent {

  /** Null while the status loads. */
  step: 'database' | 'access' | null = null;
  /** Both steps are pending, so the page counts them. */
  twoSteps = false;

  username = '';
  password = '';
  confirmPassword = '';
  setupToken = '';
  /** The server asks for the one-time token from its log. */
  tokenRequired = false;
  /** Starts on "No sign-in"; the page says when that's not safe. */
  access: AccessSettings = Object.assign(new AccessSettings(), { mode: 'NONE' });

  @ViewChild(AccessSettingsFormComponent) accessForm?: AccessSettingsFormComponent;

  router = inject(Router);
  readonly version$ = inject(AppInfoService).version$;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    this.apiService.getSetupStatus().subscribe({
      next: res => {
        const status = res.body?.data;
        this.tokenRequired = !!status?.setupTokenRequired;
        this.twoSteps = !!status?.databaseSetupRequired && !!status?.accessSetupRequired;
        if (status?.databaseSetupRequired) this.step = 'database';
        else if (status?.accessSetupRequired) this.step = 'access';
        else this.router.navigate(['/connect']);
      },
      // The submit still says what's missing if this check fails.
      error: () => {
        this.tokenRequired = true;
        this.step = 'database';
      }
    });
  }

  submit() {
    this.errors.delete('setup');
    if (this.password !== this.confirmPassword) {
      this.errors.set('setup', { code: '400', message: 'Passwords do not match.', datetime: '' });
      return;
    }
    this.flags.set('setupLoading', true);
    const setupToken = this.setupToken.trim() || undefined;
    this.apiService.configureDatabase({ username: this.username, password: this.password, setupToken }).subscribe({
      next: () => {
        this.flags.set('setupLoading', false);
        this.step = 'access';
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('setup', this.commonService.prepareError(res.error?.error, '500', 'Failed to save database credentials.'));
        this.flags.set('setupLoading', false);
      }
    });
  }

  submitAccess() {
    this.errors.delete('access');
    const problem = this.accessForm?.problem();
    if (problem) {
      this.errors.set('access', { code: '400', message: problem, datetime: '' });
      return;
    }
    this.flags.set('accessLoading', true);
    this.apiService.configureAccess({ ...this.access, setupToken: this.setupToken.trim() || undefined }).subscribe({
      next: () => {
        this.flags.set('accessLoading', false);
        // With sign-in on, this browser is signed in as the account it just gave.
        this.router.navigate(['/connect']);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('access', this.commonService.prepareError(res.error?.error, '500', 'Failed to save who may use Kafkador.'));
        this.flags.set('accessLoading', false);
      }
    });
  }

}
