import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { AuthenticationMethod, ClusterSecurityConfig, Error, GenericResponse } from '../../models';

/**
 * Settings > Security: TLS, truststore and SASL authentication of the connected cluster. Saving authentication is
 * checked against the cluster first; a password left out is asked for when connecting instead of being saved.
 */
@Component({
  selector: 'app-security-settings',
  imports: [CommonModule, FormsModule],
  templateUrl: './security-settings.component.html',
  styleUrl: './security-settings.component.scss'
})
export class SecuritySettingsComponent implements OnInit {

  readonly authenticationMethods: { value: AuthenticationMethod; label: string }[] = [
    { value: 'NONE', label: 'None' },
    { value: 'SASL_PLAIN', label: 'SASL/PLAIN' },
    { value: 'SASL_SCRAM_SHA_256', label: 'SASL/SCRAM-SHA-256' },
    { value: 'SASL_SCRAM_SHA_512', label: 'SASL/SCRAM-SHA-512' }
  ];
  readonly truststoreTypes = ['PKCS12', 'JKS'];

  config: ClusterSecurityConfig = { tls: false, authenticationMethod: 'NONE' };
  password = '';
  forgetPassword = false;
  truststoreFile?: File;
  truststoreType = 'PKCS12';
  truststorePassword = '';
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  savedAt: Map<string, Date> = new Map();

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.errors.delete('getSecurity');
    this.flags.set('securityLoading', true);
    this.apiService.getSecurityConfig().subscribe({
      next: (res: GenericResponse<ClusterSecurityConfig>) => {
        this.config = res.data;
        this.flags.set('securityLoading', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('getSecurity', this.commonService.prepareError(res.error?.error, '500', 'Failed to load the security settings!'));
        this.flags.set('securityLoading', false);
      }
    });
  }

  get usesSasl(): boolean {
    return this.config.authenticationMethod !== 'NONE';
  }

  onTruststoreFileChange(input: HTMLInputElement): void {
    this.truststoreFile = input.files?.[0];
    this.savedAt.delete('truststore');
    // Guess the type from the extension; the user can still change it.
    const name = this.truststoreFile?.name.toLowerCase() ?? '';
    if (name.endsWith('.jks')) this.truststoreType = 'JKS';
    else if (name.endsWith('.p12') || name.endsWith('.pfx') || name.endsWith('.pkcs12')) this.truststoreType = 'PKCS12';
  }

  uploadTruststore(input: HTMLInputElement): void {
    if (!this.truststoreFile) {
      this.errors.set('truststore', { code: '400', message: 'Choose a truststore file to upload.', datetime: '' });
      return;
    }
    this.errors.delete('truststore');
    this.flags.set('truststoreSaving', true);
    this.apiService.uploadTruststore(this.truststoreFile, this.truststoreType, this.truststorePassword).subscribe({
      next: (res: GenericResponse<ClusterSecurityConfig>) => {
        this.applyReadOnly(res.data);
        this.truststoreFile = undefined;
        this.truststorePassword = '';
        input.value = '';
        this.savedAt.set('truststore', new Date());
        this.flags.set('truststoreSaving', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('truststore', this.commonService.prepareError(res.error?.error, '500', 'Failed to upload the truststore!'));
        this.flags.set('truststoreSaving', false);
      }
    });
  }

  removeTruststore(): void {
    this.errors.delete('truststore');
    this.flags.set('truststoreSaving', true);
    this.apiService.removeTruststore().subscribe({
      next: (res: GenericResponse<ClusterSecurityConfig>) => {
        this.applyReadOnly(res.data);
        this.savedAt.delete('truststore');
        this.flags.set('truststoreSaving', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('truststore', this.commonService.prepareError(res.error?.error, '500', 'Failed to remove the truststore!'));
        this.flags.set('truststoreSaving', false);
      }
    });
  }

  /** Saves TLS and authentication; the server checks the cluster accepts them before keeping them. */
  saveSecurity(): void {
    if (this.usesSasl && !this.config.username?.trim()) {
      this.errors.set('saveSecurity', { code: '400', message: 'A username is required for SASL authentication.', datetime: '' });
      return;
    }
    this.errors.delete('saveSecurity');
    this.savedAt.delete('security');
    this.flags.set('securitySaving', true);
    const request: ClusterSecurityConfig = {
      tls: this.config.tls,
      authenticationMethod: this.config.authenticationMethod,
      username: this.usesSasl ? this.config.username?.trim() : '',
      password: this.usesSasl && !this.forgetPassword ? this.password : '',
      forgetPassword: this.usesSasl && this.forgetPassword
    };
    this.apiService.saveSecurityConfig(request).subscribe({
      next: (res: GenericResponse<ClusterSecurityConfig>) => {
        this.config = res.data;
        this.password = '';
        this.forgetPassword = false;
        this.savedAt.set('security', new Date());
        this.flags.set('securitySaving', false);
      },
      error: (res: HttpErrorResponse) => {
        this.errors.set('saveSecurity', this.commonService.prepareError(res.error?.error, '500', 'Failed to save the security settings!'));
        this.flags.set('securitySaving', false);
      }
    });
  }

  /** Takes the truststore state from a response without dropping unsaved edits to the authentication form. */
  private applyReadOnly(saved: ClusterSecurityConfig): void {
    this.config = { ...this.config, truststoreFileName: saved.truststoreFileName, truststoreType: saved.truststoreType };
  }

}
