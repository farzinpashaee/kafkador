import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CODESTREAMLAB_URL } from '../../app-info';
import { AppInfoService } from '../../services';

/** What Kafkador is, which version is running, and who makes it. */
@Component({
  selector: 'app-about',
  imports: [CommonModule],
  templateUrl: './about.component.html',
  styleUrl: './about.component.scss'
})
export class AboutComponent {

  readonly version$ = inject(AppInfoService).version$;
  readonly website = CODESTREAMLAB_URL;
  readonly year = new Date().getFullYear();

  readonly features: { icon: string; title: string; text: string }[] = [
    { icon: 'bi-diagram-3', title: 'Brokers', text: 'Cluster health, broker configurations and metrics from the Confluent metrics topics.' },
    { icon: 'bi-chat-left-text', title: 'Topics', text: 'Create and configure topics; browse, search, live-tail and produce records, decoding Avro, Protobuf and JSON Schema.' },
    { icon: 'bi-box-arrow-in-down', title: 'Consumers', text: 'Consumer groups, their members and lag per partition, and resetting their offsets.' },
    { icon: 'bi-shield', title: 'Access Control', text: 'List, create, edit and delete ACL bindings.' },
    { icon: 'bi-database', title: 'KsqlDB', text: 'Run queries and find streams and tables.' },
    { icon: 'bi-box-arrow-in-right', title: 'Connectors', text: 'Manage Kafka Connect connectors and plugins.' },
    { icon: 'bi-layers', title: 'Schema Registry', text: 'Subjects, schema versions, diffs and compatibility settings.' },
    { icon: 'bi-hdd-network', title: 'Connections', text: 'Switch between saved clusters, with or without credentials.' },
    { icon: 'bi-chat-dots', title: 'AI Assistant', text: 'Ask questions about the connected cluster in plain language.' }
  ];

}
