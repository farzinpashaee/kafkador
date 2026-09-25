import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { AiAssistantService, ApiService, CommonService } from '../../services';
import { GenericResponse, KsqlDbConfig, SchemaRegistryConfig, KafkaConnectConfig, CompatibilityConfig, ConnectorPlugin, AiConfig, Error } from '../../models';

@Component({
  selector: 'app-settings',
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {

  readonly aiProviders = [
    { key: 'openai', label: 'OpenAI (ChatGPT)', defaultModel: 'gpt-4o-mini' },
    { key: 'anthropic', label: 'Anthropic (Claude)', defaultModel: 'claude-sonnet-5' },
    { key: 'gemini', label: 'Google (Gemini)', defaultModel: 'gemini-2.0-flash' }
  ];

  readonly compatibilityLevels =['BACKWARD', 'BACKWARD_TRANSITIVE', 'FORWARD', 'FORWARD_TRANSITIVE', 'FULL', 'FULL_TRANSITIVE', 'NONE'];

  ksqlDbConfig: KsqlDbConfig = { configured: false, url: '' };
  schemaRegistryConfig: SchemaRegistryConfig = { configured: false, url: '' };
  kafkaConnectConfig: KafkaConnectConfig = { configured: false, url: '' };
  connectorPlugins: ConnectorPlugin[] = [];
  aiConfig: AiConfig = { provider: 'openai', enabled: false };
  aiApiKey = '';
  globalCompatibility: CompatibilityConfig = {};
  selectedCompatibilityLevel = 'BACKWARD';
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  savedAt: Map<string, Date> = new Map();

  route = inject(ActivatedRoute);

  constructor(private apiService: ApiService, private commonService: CommonService, private aiAssistant: AiAssistantService) {}

  ngOnInit() {
    const fragment = this.route.snapshot.fragment;
    if (fragment) {
      setTimeout(() => this.commonService.showTab(fragment + '-tab'));
    }
    this.getKsqlDbConfig();
    this.getSchemaRegistryConfig();
    this.getKafkaConnectConfig();
    this.getGlobalCompatibility();
    this.getAiConfig();
  }

  getKsqlDbConfig() {
    this.errors.delete('getKsqlDbConfig');
    this.flags.set('ksqlDbConfigLoading', true);
    this.apiService.getKsqlDbConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlDbConfig>>) => {
        this.ksqlDbConfig = res.body?.data ?? { configured: false, url: '' };
        this.flags.set('ksqlDbConfigLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        if (res.status === 404) {
          this.ksqlDbConfig = { configured: false, url: '' };
        } else {
          this.errors.set('getKsqlDbConfig', this.commonService.prepareError(res.error.error,'500','Failed to load ksqlDB configuration!'));
        }
        this.flags.set('ksqlDbConfigLoading', false);
      }
    });
  }

  saveKsqlDbConfig() {
    if (!this.ksqlDbConfig.url) {
      this.errors.set('saveKsqlDbConfig', {code:'400',message:'URL is required',datetime:''});
      return;
    }
    this.errors.delete('saveKsqlDbConfig');
    this.savedAt.delete('ksqlDbConfig');
    this.flags.set('ksqlDbConfigSaving', true);
    this.apiService.saveKsqlDbConfig(this.ksqlDbConfig.url).subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlDbConfig>>) => {
        this.ksqlDbConfig = res.body?.data ?? this.ksqlDbConfig;
        this.savedAt.set('ksqlDbConfig', new Date());
        this.flags.set('ksqlDbConfigSaving', false);
      },
      error: (res:HttpErrorResponse) => {
        this.savedAt.delete('ksqlDbConfig');
        this.errors.set('saveKsqlDbConfig', this.commonService.prepareError(res.error.error,'500','Failed to save ksqlDB configuration!'));
        this.flags.set('ksqlDbConfigSaving', false);
      }
    });
  }

  getSchemaRegistryConfig() {
    this.errors.delete('getSchemaRegistryConfig');
    this.flags.set('schemaRegistryConfigLoading', true);
    this.apiService.getSchemaRegistryConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaRegistryConfig>>) => {
        this.schemaRegistryConfig = res.body?.data ?? { configured: false, url: '' };
        this.flags.set('schemaRegistryConfigLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        if (res.status === 404) {
          this.schemaRegistryConfig = { configured: false, url: '' };
        } else {
          this.errors.set('getSchemaRegistryConfig', this.commonService.prepareError(res.error.error,'500','Failed to load Schema Registry configuration!'));
        }
        this.flags.set('schemaRegistryConfigLoading', false);
      }
    });
  }

  getGlobalCompatibility() {
    this.errors.delete('getGlobalCompatibility');
    this.flags.set('globalCompatibilityLoading', true);
    this.apiService.getGlobalCompatibility().subscribe({
      next: (res: HttpResponse<GenericResponse<CompatibilityConfig>>) => {
        this.globalCompatibility = res.body?.data ?? {};
        this.selectedCompatibilityLevel = this.globalCompatibility.level || 'BACKWARD';
        this.flags.set('globalCompatibilityLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        // Schema Registry not configured yet, or unreachable — leave the default selection in place.
        this.flags.set('globalCompatibilityLoading', false);
      }
    });
  }

  saveSchemaRegistrySettings() {
    if (!this.schemaRegistryConfig.url) {
      this.errors.set('saveSchemaRegistrySettings', {code:'400',message:'URL is required',datetime:''});
      return;
    }
    this.errors.delete('saveSchemaRegistrySettings');
    this.savedAt.delete('schemaRegistrySettings');
    this.flags.set('schemaRegistrySettingsSaving', true);
    this.apiService.saveSchemaRegistryConfig(this.schemaRegistryConfig.url).subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaRegistryConfig>>) => {
        this.schemaRegistryConfig = res.body?.data ?? this.schemaRegistryConfig;
        this.apiService.saveGlobalCompatibility(this.selectedCompatibilityLevel).subscribe({
          next: (res2: HttpResponse<GenericResponse<CompatibilityConfig>>) => {
            this.globalCompatibility = res2.body?.data ?? { level: this.selectedCompatibilityLevel };
            this.savedAt.set('schemaRegistrySettings', new Date());
            this.flags.set('schemaRegistrySettingsSaving', false);
          },
          error: (res2:HttpErrorResponse) => {
            this.errors.set('saveSchemaRegistrySettings', this.commonService.prepareError(res2.error?.error,'500','Failed to save compatibility level!'));
            this.flags.set('schemaRegistrySettingsSaving', false);
          }
        });
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('saveSchemaRegistrySettings', this.commonService.prepareError(res.error.error,'500','Failed to save Schema Registry configuration!'));
        this.flags.set('schemaRegistrySettingsSaving', false);
      }
    });
  }

  getKafkaConnectConfig() {
    this.errors.delete('getKafkaConnectConfig');
    this.flags.set('kafkaConnectConfigLoading', true);
    this.apiService.getKafkaConnectConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<KafkaConnectConfig>>) => {
        this.kafkaConnectConfig = res.body?.data ?? { configured: false, url: '' };
        this.flags.set('kafkaConnectConfigLoading', false);
        if (this.kafkaConnectConfig.configured) this.getConnectorPlugins();
      },
      error: (res:HttpErrorResponse) => {
        if (res.status === 404) {
          this.kafkaConnectConfig = { configured: false, url: '' };
        } else {
          this.errors.set('getKafkaConnectConfig', this.commonService.prepareError(res.error.error,'500','Failed to load Kafka Connect configuration!'));
        }
        this.flags.set('kafkaConnectConfigLoading', false);
      }
    });
  }

  saveKafkaConnectConfig() {
    if (!this.kafkaConnectConfig.url) {
      this.errors.set('saveKafkaConnectConfig', {code:'400',message:'URL is required',datetime:''});
      return;
    }
    this.errors.delete('saveKafkaConnectConfig');
    this.savedAt.delete('kafkaConnectConfig');
    this.flags.set('kafkaConnectConfigSaving', true);
    this.apiService.saveKafkaConnectConfig(this.kafkaConnectConfig.url).subscribe({
      next: (res: HttpResponse<GenericResponse<KafkaConnectConfig>>) => {
        this.kafkaConnectConfig = res.body?.data ?? this.kafkaConnectConfig;
        this.savedAt.set('kafkaConnectConfig', new Date());
        this.flags.set('kafkaConnectConfigSaving', false);
        if (this.kafkaConnectConfig.configured) this.getConnectorPlugins();
      },
      error: (res:HttpErrorResponse) => {
        this.savedAt.delete('kafkaConnectConfig');
        this.errors.set('saveKafkaConnectConfig', this.commonService.prepareError(res.error.error,'500','Failed to save Kafka Connect configuration!'));
        this.flags.set('kafkaConnectConfigSaving', false);
      }
    });
  }

  get aiDefaultModel(): string {
    return this.aiProviders.find(p => p.key === this.aiConfig.provider)?.defaultModel ?? '';
  }

  getAiConfig() {
    this.errors.delete('getAiConfig');
    this.flags.set('aiConfigLoading', true);
    this.apiService.getAiConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<AiConfig>>) => {
        this.aiConfig = res.body?.data ?? { provider: 'openai', enabled: false };
        this.flags.set('aiConfigLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('getAiConfig', this.commonService.prepareError(res.error?.error,'500','Failed to load AI assistant configuration!'));
        this.flags.set('aiConfigLoading', false);
      }
    });
  }

  saveAiConfig() {
    if (this.aiConfig.enabled && !this.aiConfig.apiKeySet && !this.aiApiKey.trim()) {
      this.errors.set('saveAiConfig', {code:'400',message:'An API key is required to enable the AI assistant.',datetime:''});
      return;
    }
    this.errors.delete('saveAiConfig');
    this.savedAt.delete('aiConfig');
    this.flags.set('aiConfigSaving', true);
    const payload: AiConfig = {
      provider: this.aiConfig.provider,
      model: (this.aiConfig.model ?? '').trim(),
      baseUrl: (this.aiConfig.baseUrl ?? '').trim(),
      enabled: this.aiConfig.enabled,
      apiKey: this.aiApiKey.trim()
    };
    this.apiService.saveAiConfig(payload).subscribe({
      next: (res: HttpResponse<GenericResponse<AiConfig>>) => {
        this.aiConfig = res.body?.data ?? this.aiConfig;
        this.aiApiKey = '';
        this.aiAssistant.setAvailable(this.aiConfig.available === true);
        this.savedAt.set('aiConfig', new Date());
        this.flags.set('aiConfigSaving', false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('saveAiConfig', this.commonService.prepareError(res.error?.error,'500','Failed to save AI assistant configuration!'));
        this.flags.set('aiConfigSaving', false);
      }
    });
  }

  getConnectorPlugins() {
    this.errors.delete('getConnectorPlugins');
    this.flags.set('connectorPluginsLoading', true);
    this.apiService.getConnectorPlugins().subscribe({
      next: (res: HttpResponse<GenericResponse<ConnectorPlugin[]>>) => {
        this.connectorPlugins = res.body?.data ?? [];
        this.flags.set('connectorPluginsLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('getConnectorPlugins', this.commonService.prepareError(res.error?.error,'500','Failed to load connector plugins!'));
        this.flags.set('connectorPluginsLoading', false);
      }
    });
  }

}
