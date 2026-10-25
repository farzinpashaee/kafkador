import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ServiceAuthFormComponent } from './service-auth-form.component';
import { ServiceAuth, withAuth } from '../../models';

describe('ServiceAuthFormComponent', () => {
  let component: ServiceAuthFormComponent;
  let fixture: ComponentFixture<ServiceAuthFormComponent>;
  let page: HTMLElement;

  async function create(auth: ServiceAuth) {
    await TestBed.configureTestingModule({ imports: [ServiceAuthFormComponent] }).compileComponents();
    fixture = TestBed.createComponent(ServiceAuthFormComponent);
    component = fixture.componentInstance;
    component.auth = auth;
    component.idPrefix = 'sr';
    component.serviceName = 'Schema Registry';
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    page = fixture.nativeElement;
  }

  it('asks only for what the chosen method needs', async () => {
    await create({ method: 'NONE' });
    expect(page.querySelector('#srAuthUsername')).toBeNull();
    expect(page.querySelector('#srAuthToken')).toBeNull();

    component.auth.method = 'BASIC';
    fixture.detectChanges();
    expect(page.querySelector('#srAuthUsername')).not.toBeNull();
    expect(page.querySelector('#srAuthPassword')!.getAttribute('type')).toBe('password');

    component.auth.method = 'BEARER';
    fixture.detectChanges();
    expect(page.querySelector('#srAuthUsername')).toBeNull();
    expect(page.querySelector('#srAuthToken')).not.toBeNull();
  });

  it('marks a saved password instead of showing it', async () => {
    await create({ method: 'BASIC', username: 'api-key', passwordSaved: true });

    const password = page.querySelector<HTMLInputElement>('#srAuthPassword')!;
    expect(password.value).toBe('');
    expect(password.placeholder).toContain('Saved');
  });

  it('shows a saved CA certificate by its subject, and can remove it or keep it', async () => {
    const changed = jasmine.createSpy('changed');
    await create({ method: 'NONE', caCertificateSubject: 'CN=Aiven Project CA' });
    component.changed.subscribe(changed);
    expect(page.textContent).toContain('CN=Aiven Project CA');
    expect(page.querySelector('#srCaCertificate')).toBeNull();

    component.removeCertificate();
    fixture.detectChanges();
    expect(component.auth.clearCaCertificate).toBeTrue();
    expect(page.textContent).toContain('will be removed when you save');

    component.keepCertificate();
    fixture.detectChanges();
    expect(component.auth.clearCaCertificate).toBeFalse();
    expect(page.textContent).toContain('CN=Aiven Project CA');
    expect(changed).toHaveBeenCalledTimes(2);
  });

  it('loads a PEM file into the certificate box and turns away other files', async () => {
    await create({ method: 'NONE' });
    const input = document.createElement('input');
    const pem = new File(['-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n'], 'ca.pem');
    Object.defineProperty(input, 'files', { value: [pem], configurable: true });
    await component.loadCertificateFile({ target: input } as unknown as Event);
    expect(component.auth.caCertificate).toContain('BEGIN CERTIFICATE');

    const notPem = new File(['hello'], 'notes.txt');
    Object.defineProperty(input, 'files', { value: [notPem], configurable: true });
    await component.loadCertificateFile({ target: input } as unknown as Event);
    expect(component.certificateFileError).toContain('notes.txt');
  });

  it('gives configs from older servers an empty sign-in', () => {
    expect(withAuth({ configured: true, url: 'http://sr:8081' }).auth).toEqual({ method: 'NONE' });
    expect(withAuth(undefined)).toEqual({ configured: false, url: '', auth: { method: 'NONE' } });
  });
});
