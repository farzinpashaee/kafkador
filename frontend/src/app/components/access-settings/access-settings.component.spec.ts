import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { AccessSettingsComponent } from './access-settings.component';
import { ApiService } from '../../services';

describe('AccessSettingsComponent', () => {
  let component: AccessSettingsComponent;
  let fixture: ComponentFixture<AccessSettingsComponent>;
  let api: jasmine.SpyObj<ApiService>;
  let router: Router;

  async function create(saved: object) {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getAccessSettings', 'saveAccessSettings']);
    api.getAccessSettings.and.returnValue(of({ body: { data: saved } } as any));
    await TestBed.configureTestingModule({
      imports: [AccessSettingsComponent],
      providers: [provideRouter([]), { provide: ApiService, useValue: api }]
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(AccessSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('keeps the admin password when only the form is saved again', async () => {
    await create({ mode: 'DATABASE', username: 'admin' });
    api.saveAccessSettings.and.returnValue(of({ body: { data: { loginRequired: true, authenticated: true } } } as any));

    component.save();

    expect(api.saveAccessSettings).toHaveBeenCalledWith(jasmine.objectContaining({ mode: 'DATABASE', username: 'admin', password: '' }));
    expect(component.savedAt).toBeDefined();
  });

  it('goes to the sign-in page when the save signed this browser out', async () => {
    await create({ mode: 'NONE' });
    component.settings.mode = 'LDAP';
    component.settings.ldap.url = 'ldap://ldap:389';
    component.settings.testUsername = 'alice';
    component.settings.testPassword = 'pw';
    api.saveAccessSettings.and.returnValue(of({ body: { data: { loginRequired: true, authenticated: false } } } as any));

    component.save();

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('only explains when KAFKADOR_ACCESS_MODE decides', async () => {
    await create({ mode: 'NONE', managedByEnvironment: true });
    const page = fixture.nativeElement as HTMLElement;

    expect(page.textContent).toContain('KAFKADOR_ACCESS_MODE');
    expect(page.querySelector('form')).toBeNull();
  });
});
