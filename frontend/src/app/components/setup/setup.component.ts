import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { Error } from '../../models';

@Component({
  selector: 'app-setup',
  imports: [CommonModule, FormsModule],
  templateUrl: './setup.component.html',
  styleUrl: './setup.component.scss'
})
export class SetupComponent {

  username = '';
  password = '';
  confirmPassword = '';

  router = inject(Router);
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  submit() {
    this.errors.delete('setup');
    if (this.password !== this.confirmPassword) {
      this.errors.set('setup', { code: '400', message: 'Passwords do not match.', datetime: '' });
      return;
    }
    this.flags.set('setupLoading', true);
    this.apiService.configureDatabase({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.flags.set('setupLoading', false);
        this.router.navigate(['/connect']);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('setup', this.commonService.prepareError(res.error?.error, '500', 'Failed to save database credentials.'));
        this.flags.set('setupLoading', false);
      }
    });
  }

}
