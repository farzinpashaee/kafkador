import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { SetupComponent } from './setup.component';
import { ApiService } from '../../services';

describe('SetupComponent', () => {
  let component: SetupComponent;
  let fixture: ComponentFixture<SetupComponent>;
  let api: jasmine.SpyObj<ApiService>;

  async function create(tokenRequired: boolean, databaseSetupRequired = true, accessSetupRequired = true) {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getSetupStatus', 'configureDatabase', 'configureAccess']);
    api.getSetupStatus.and.returnValue(of({ body: { data: { databaseSetupRequired, accessSetupRequired, setupTokenRequired: tokenRequired } } } as any));
    api.configureDatabase.and.returnValue(of({ body: { data: null } } as any));
    api.configureAccess.and.returnValue(of({ body: { data: null } } as any));
    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [provideRouter([{ path: 'connect', children: [] }]), provideHttpClient(), provideHttpClientTesting(), { provide: ApiService, useValue: api }]
    }).compileComponents();
    spyOn(TestBed.inject(Router), 'navigate');
    fixture = TestBed.createComponent(SetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function page(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function fill(token = '') {
    component.username = 'kafkador_admin';
    component.password = component.confirmPassword = 'a-long-password';
    component.setupToken = token;
  }

  it('asks for the setup token from the log when the server wants one, and sends it', async () => {
    await create(true);
    expect(page().querySelector('#setupToken')).not.toBeNull();

    fill(' 0123abcd ');
    component.submit();

    expect(api.configureDatabase).toHaveBeenCalledWith({ username: 'kafkador_admin', password: 'a-long-password', setupToken: '0123abcd' });
  });

  it('leaves the token out when the server does not ask for one', async () => {
    await create(false);
    expect(page().querySelector('#setupToken')).toBeNull();

    fill();
    component.submit();

    expect(api.configureDatabase).toHaveBeenCalledWith({ username: 'kafkador_admin', password: 'a-long-password', setupToken: undefined });
  });

  it('shows why a token was refused', async () => {
    await create(true);
    api.configureDatabase.and.returnValue(throwError(() => ({ status: 403,
      error: { error: { code: '403', message: 'That setup token is wrong.', datetime: '' } } })));

    fill('nope');
    component.submit();

    expect(component.errors.get('setup')?.message).toContain('setup token is wrong');
  });

  it('then asks who may use Kafkador, sending the same token', async () => {
    await create(true);
    fill('0123abcd');
    component.submit();

    expect(component.step).toBe('access');
    expect(page().textContent).toContain('Step 2 of 2');
    // The token was given in step 1.
    expect(page().querySelector('#accessSetupToken')).toBeNull();

    component.access.mode = 'DATABASE';
    component.access.username = 'admin';
    component.access.password = 'admin-password';
    page();
    component.accessForm!.confirmPassword = 'admin-password';
    component.submitAccess();

    expect(api.configureAccess).toHaveBeenCalledWith(jasmine.objectContaining({ mode: 'DATABASE', username: 'admin', setupToken: '0123abcd' }));
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/connect']);
  });

  it('asks an instance set up before sign-in existed only who may use it, with the token', async () => {
    await create(true, false, true);

    expect(component.step).toBe('access');
    expect(page().querySelector('#username')).toBeNull();
    expect(page().querySelector('#accessSetupToken')).not.toBeNull();
    expect(page().textContent).not.toContain('Step 2 of 2');
  });

  it('starts on "No sign-in", listed first', async () => {
    await create(false, false, true);

    const first = page().querySelector<HTMLInputElement>('input[type="radio"]');
    expect(first?.id).toBe('setupAccessNONE');
    expect(component.access.mode).toBe('NONE');

    component.submitAccess();
    expect(api.configureAccess).toHaveBeenCalledWith(jasmine.objectContaining({ mode: 'NONE' }));
  });

  it('does not save until the access form is complete', async () => {
    await create(false, false, true);
    component.access.mode = 'DATABASE';
    page();

    component.submitAccess();

    expect(component.errors.get('access')?.message).toContain('admin account');
    expect(api.configureAccess).not.toHaveBeenCalled();
  });

  it('goes on to Connect when nothing is left to set up', async () => {
    await create(false, false, false);

    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/connect']);
  });
});
