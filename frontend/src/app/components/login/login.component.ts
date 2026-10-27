import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, AppInfoService, CommonService } from '../../services';
import { AccessMode, Error } from '../../models';
import { CODESTREAMLAB_URL } from '../../app-info';

/** Signing in to Kafkador, when setup or Settings turned sign-in on. */
@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: '../setup/setup.component.scss'
})
export class LoginComponent {

  username = '';
  password = '';
  mode?: AccessMode | null;

  router = inject(Router);
  readonly version$ = inject(AppInfoService).version$;
  readonly website = CODESTREAMLAB_URL;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    // Also gives the browser the XSRF-TOKEN cookie the sign-in request needs.
    this.apiService.getAccessStatus().subscribe({
      next: res => {
        const status = res.body?.data;
        this.mode = status?.mode;
        if (status && (!status.loginRequired || status.authenticated)) this.router.navigate(['/']);
      },
      error: () => {}
    });
  }

  submit() {
    this.errors.delete('login');
    if (!this.username.trim() || !this.password) {
      this.errors.set('login', { code: '400', message: 'Enter your username and password.', datetime: '' });
      return;
    }
    this.flags.set('loginLoading', true);
    this.apiService.login({ username: this.username.trim(), password: this.password }).subscribe({
      next: () => {
        this.flags.set('loginLoading', false);
        this.router.navigate(['/']);
      },
      error: (res: HttpErrorResponse) => {
        this.password = '';
        this.errors.set('login', this.commonService.prepareError(res.error?.error, '500', 'Signing in failed.'));
        this.flags.set('loginLoading', false);
      }
    });
  }

}
