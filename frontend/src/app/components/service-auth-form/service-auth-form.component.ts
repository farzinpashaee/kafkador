import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ServiceAuth, ServiceAuthMethod } from '../../models';

/**
 * Sign-in and TLS fields for Schema Registry, Kafka Connect and ksqlDB in Settings. Edits {@link auth} in place; the
 * page saves it with the URL. Saved secrets are never shown: they're marked as saved, and left blank to keep them.
 */
@Component({
  selector: 'app-service-auth-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './service-auth-form.component.html'
})
export class ServiceAuthFormComponent {

  @Input({ required: true }) auth!: ServiceAuth;
  /** Keeps element ids unique when the form appears on several tabs. */
  @Input({ required: true }) idPrefix!: string;
  /** Named in the help text, e.g. "Schema Registry". */
  @Input() serviceName = 'the server';
  @Output() changed = new EventEmitter<void>();

  readonly methods: { value: ServiceAuthMethod; label: string }[] = [
    { value: 'NONE', label: 'None' },
    { value: 'BASIC', label: 'Username and password, or API key and secret' },
    { value: 'BEARER', label: 'Bearer token' }
  ];

  /** Shows the certificate box over a saved certificate, to replace it. */
  replacingCertificate = false;
  certificateFileError?: string;

  get hasSavedCertificate(): boolean {
    return !!this.auth.caCertificateSubject && !this.auth.clearCaCertificate;
  }

  edited(): void {
    this.changed.emit();
  }

  removeCertificate(): void {
    this.auth.clearCaCertificate = true;
    this.auth.caCertificate = '';
    this.replacingCertificate = false;
    this.edited();
  }

  keepCertificate(): void {
    this.auth.clearCaCertificate = false;
    this.auth.caCertificate = '';
    this.replacingCertificate = false;
    this.edited();
  }

  /** Reads a .pem / .crt file into the certificate box; the server checks it when saving. */
  loadCertificateFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return Promise.resolve();
    this.certificateFileError = undefined;
    return file.text().then(text => {
      if (!text.includes('-----BEGIN CERTIFICATE-----')) {
        this.certificateFileError = `${file.name} isn't a PEM certificate (it should contain -----BEGIN CERTIFICATE-----).`;
        return;
      }
      this.auth.caCertificate = text.trim();
      this.auth.clearCaCertificate = false;
      this.edited();
    }).finally(() => input.value = '');
  }

}
