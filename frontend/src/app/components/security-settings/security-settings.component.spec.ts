import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { SecuritySettingsComponent } from './security-settings.component';
import { ApiService } from '../../services';
import { ClusterSecurityConfig } from '../../models';

describe('SecuritySettingsComponent', () => {
  let component: SecuritySettingsComponent;
  let fixture: ComponentFixture<SecuritySettingsComponent>;
  let api: jasmine.SpyObj<ApiService>;

  const saved: ClusterSecurityConfig = { tls: true, authenticationMethod: 'SASL_SCRAM_SHA_512', username: 'farzin',
    passwordSaved: true, truststoreFileName: 'cluster-ca.p12', truststoreType: 'PKCS12' };

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getSecurityConfig', 'saveSecurityConfig', 'uploadTruststore', 'removeTruststore']);
    api.getSecurityConfig.and.callFake(() => of({ data: { ...saved } } as any));
    api.saveSecurityConfig.and.callFake((config: ClusterSecurityConfig) => of({ data: { ...saved, ...config, password: undefined } } as any));

    await TestBed.configureTestingModule({
      imports: [SecuritySettingsComponent],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();

    fixture = TestBed.createComponent(SecuritySettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the saved settings without the password', () => {
    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('cluster-ca.p12');
    expect(page.querySelector<HTMLInputElement>('#securityPassword')!.placeholder).toContain('(saved)');
    expect(page.querySelector<HTMLInputElement>('#securityPassword')!.value).toBe('');
  });

  it('sends a new password only when one is typed, and can forget the saved one', () => {
    component.saveSecurity();
    expect(api.saveSecurityConfig.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ password: '', forgetPassword: false }));

    component.password = 'new-secret';
    component.saveSecurity();
    expect(api.saveSecurityConfig.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ password: 'new-secret' }));
    expect(component.password).toBe('');

    component.forgetPassword = true;
    component.saveSecurity();
    expect(api.saveSecurityConfig.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ password: '', forgetPassword: true }));
  });

  it('needs a username for SASL and drops it for no authentication', () => {
    component.config.username = ' ';
    component.saveSecurity();
    expect(api.saveSecurityConfig).not.toHaveBeenCalled();
    expect(component.errors.get('saveSecurity')).toBeTruthy();

    component.config.authenticationMethod = 'NONE';
    component.saveSecurity();
    expect(api.saveSecurityConfig.calls.mostRecent().args[0]).toEqual(jasmine.objectContaining({ authenticationMethod: 'NONE', username: '' }));
  });

  it('shows why the cluster rejected the settings', () => {
    api.saveSecurityConfig.and.returnValue(throwError(() => ({ status: 400,
      error: { error: { message: "The cluster didn't accept a connection with these settings, so they weren't saved." } } })));
    component.saveSecurity();
    expect(component.errors.get('saveSecurity')?.message).toContain("weren't saved");
    expect(component.flags.get('securitySaving')).toBeFalse();
  });

  it('uploads the chosen truststore with its type and password, keeping unsaved form edits', () => {
    api.uploadTruststore.and.returnValue(of({ data: { ...saved, truststoreFileName: 'new-ca.jks', truststoreType: 'JKS' } } as any));
    const input = document.createElement('input');
    const file = new File(['x'], 'new-ca.jks');
    Object.defineProperty(input, 'files', { value: [file] });
    component.onTruststoreFileChange(input);
    expect(component.truststoreType).toBe('JKS');

    component.config.username = 'edited-not-saved';
    component.truststorePassword = 'changeit';
    component.uploadTruststore(input);

    expect(api.uploadTruststore).toHaveBeenCalledWith(file, 'JKS', 'changeit');
    expect(component.config.truststoreFileName).toBe('new-ca.jks');
    expect(component.config.username).toBe('edited-not-saved');
    expect(component.truststorePassword).toBe('');
  });

  it('removes the truststore', () => {
    api.removeTruststore.and.returnValue(of({ data: { ...saved, truststoreFileName: null, truststoreType: null } } as any));
    component.removeTruststore();
    expect(component.config.truststoreFileName).toBeNull();
  });
});
