import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, CommonService } from '../../services';
import { GenericResponse, KsqlDbConfig, SchemaRegistryConfig, Error } from '../../models';

@Component({
  selector: 'app-settings',
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {

  ksqlDbConfig: KsqlDbConfig = { configured: false, url: '' };
  schemaRegistryConfig: SchemaRegistryConfig = { configured: false, url: '' };
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  savedAt: Map<string, Date> = new Map();

  route = inject(ActivatedRoute);

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    const fragment = this.route.snapshot.fragment;
    if (fragment) {
      setTimeout(() => this.commonService.showTab(fragment + '-tab'));
    }
    this.getKsqlDbConfig();
    this.getSchemaRegistryConfig();
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

  saveSchemaRegistryConfig() {
    if (!this.schemaRegistryConfig.url) {
      this.errors.set('saveSchemaRegistryConfig', {code:'400',message:'URL is required',datetime:''});
      return;
    }
    this.errors.delete('saveSchemaRegistryConfig');
    this.savedAt.delete('schemaRegistryConfig');
    this.flags.set('schemaRegistryConfigSaving', true);
    this.apiService.saveSchemaRegistryConfig(this.schemaRegistryConfig.url).subscribe({
      next: (res: HttpResponse<GenericResponse<SchemaRegistryConfig>>) => {
        this.schemaRegistryConfig = res.body?.data ?? this.schemaRegistryConfig;
        this.savedAt.set('schemaRegistryConfig', new Date());
        this.flags.set('schemaRegistryConfigSaving', false);
      },
      error: (res:HttpErrorResponse) => {
        this.savedAt.delete('schemaRegistryConfig');
        this.errors.set('saveSchemaRegistryConfig', this.commonService.prepareError(res.error.error,'500','Failed to save Schema Registry configuration!'));
        this.flags.set('schemaRegistryConfigSaving', false);
      }
    });
  }

}
