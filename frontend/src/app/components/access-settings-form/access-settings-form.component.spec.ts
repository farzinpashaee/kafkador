import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccessSettingsFormComponent } from './access-settings-form.component';
import { AccessSettings } from '../../models';

describe('AccessSettingsFormComponent', () => {
  let component: AccessSettingsFormComponent;
  let fixture: ComponentFixture<AccessSettingsFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AccessSettingsFormComponent] }).compileComponents();
    fixture = TestBed.createComponent(AccessSettingsFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('settings', new AccessSettings());
    fixture.detectChanges();
  });

  function page(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows only the fields of the chosen mode', () => {
    expect(page().querySelector('#accessUsername')).toBeNull();

    component.settings.mode = 'DATABASE';
    expect(page().querySelector('#accessUsername')).not.toBeNull();
    expect(page().querySelector('#accessLdapUrl')).toBeNull();

    component.settings.mode = 'LDAP';
    expect(page().querySelector('#accessLdapUrl')).not.toBeNull();
    expect(page().querySelector('#accessTestUsername')).not.toBeNull();
  });

  it('wants matching passwords for a new admin account', () => {
    component.settings.mode = 'DATABASE';
    component.settings.username = 'admin';
    expect(component.problem()).toContain('password');

    component.settings.password = 'a-long-password';
    component.confirmPassword = 'another';
    expect(component.problem()).toBe('Passwords do not match.');

    component.confirmPassword = 'a-long-password';
    expect(component.problem()).toBeNull();
  });

  it('lets Settings keep the current admin password', () => {
    fixture.componentRef.setInput('savedUsername', 'admin');
    component.settings.mode = 'DATABASE';
    component.settings.username = 'admin';
    expect(component.problem()).toBeNull();

    component.settings.username = 'someone-else';
    expect(component.problem()).toContain('password');
  });

  it('wants an LDAP account to check the settings with', () => {
    component.settings.mode = 'LDAP';
    component.settings.ldap.url = 'ldap://ldap:389';
    expect(component.problem()).toContain('LDAP username and password');

    component.settings.testUsername = 'alice';
    component.settings.testPassword = 'pw';
    expect(component.problem()).toBeNull();
  });

  it('clears the other way of finding LDAP users when switching', () => {
    component.settings.mode = 'LDAP';
    component.settings.ldap.userSearchFilter = '(uid={0})';
    component.setLdapFind('pattern');
    component.settings.ldap.userDnPattern = 'uid={0},ou=people';

    expect(component.settings.ldap.userSearchFilter).toBe('');
    component.setLdapFind('search');
    expect(component.settings.ldap.userDnPattern).toBe('');
  });
});
