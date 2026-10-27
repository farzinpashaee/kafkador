import { Component, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { AccessSettings, Error, LdapSettings } from '../../models';
import { AccessSettingsFormComponent } from '../access-settings-form/access-settings-form.component';

/** Settings > Access: who may use Kafkador. Saving signs everyone else out. */
@Component({
  selector: 'app-access-settings',
  imports: [CommonModule, FormsModule, AccessSettingsFormComponent],
  templateUrl: './access-settings.component.html'
})
export class AccessSettingsComponent {

  settings: AccessSettings = new AccessSettings();
  /** The admin username as saved, so the form knows a blank password keeps the current one. */
  savedUsername = '';
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  savedAt?: Date;

  @ViewChild(AccessSettingsFormComponent) form?: AccessSettingsFormComponent;

  router = inject(Router);

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.errors.delete('load');
    this.flags.set('loading', true);
    this.apiService.getAccessSettings().subscribe({
      next: res => {
        const saved = res.body?.data;
        const settings = new AccessSettings();
        settings.mode = saved?.mode;
        settings.username = saved?.username ?? '';
        settings.ldap = Object.assign(new LdapSettings(), saved?.ldap ?? {}, { managerPassword: '' });
        settings.managedByEnvironment = saved?.managedByEnvironment;
        this.settings = settings;
        this.savedUsername = settings.mode === 'DATABASE' ? settings.username : '';
        this.flags.set('loading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('load', this.commonService.prepareError(res.error?.error, '500', 'Failed to load who may use Kafkador.'));
        this.flags.set('loading', false);
      }
    });
  }

  save() {
    this.errors.delete('save');
    this.savedAt = undefined;
    const problem = this.form?.problem();
    if (problem) {
      this.errors.set('save', { code: '400', message: problem, datetime: '' });
      return;
    }
    this.flags.set('saving', true);
    this.apiService.saveAccessSettings(this.settings).subscribe({
      next: res => {
        this.flags.set('saving', false);
        const status = res.body?.data;
        if (status?.loginRequired && !status.authenticated) {
          this.router.navigate(['/login']);
          return;
        }
        this.savedAt = new Date();
        this.load();
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('save', this.commonService.prepareError(res.error?.error, '500', 'Failed to save who may use Kafkador.'));
        this.flags.set('saving', false);
      }
    });
  }

}
