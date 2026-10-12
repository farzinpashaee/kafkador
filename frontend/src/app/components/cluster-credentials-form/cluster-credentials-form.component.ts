import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClusterCredentials } from '../../models';

/** Fields for a cluster's SASL credentials; edits the given object in place. */
@Component({
  selector: 'app-cluster-credentials-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './cluster-credentials-form.component.html'
})
export class ClusterCredentialsFormComponent {

  @Input({ required: true }) credentials!: ClusterCredentials;
  /** Keeps element ids unique when the form appears in more than one dialog. */
  @Input() idPrefix = 'credentials';

  readonly protocols: { value: ClusterCredentials['securityProtocol']; label: string }[] = [
    { value: 'SASL_PLAINTEXT', label: 'SASL_PLAINTEXT' },
    { value: 'SASL_SSL', label: 'SASL_SSL' }
  ];
  readonly mechanisms: { value: ClusterCredentials['saslMechanism']; label: string }[] = [
    { value: 'PLAIN', label: 'PLAIN' },
    { value: 'SCRAM_SHA_256', label: 'SCRAM-SHA-256' },
    { value: 'SCRAM_SHA_512', label: 'SCRAM-SHA-512' }
  ];

}
