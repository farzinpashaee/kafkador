import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { SetupComponent } from './setup.component';
import { ApiService } from '../../services';

describe('SetupComponent', () => {
  let component: SetupComponent;
  let fixture: ComponentFixture<SetupComponent>;
  let api: jasmine.SpyObj<ApiService>;

  async function create(tokenRequired: boolean) {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getSetupStatus', 'configureDatabase']);
    api.getSetupStatus.and.returnValue(of({ body: { data: { databaseSetupRequired: true, setupTokenRequired: tokenRequired } } } as any));
    api.configureDatabase.and.returnValue(of({ body: { data: null } } as any));
    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [provideRouter([{ path: 'connect', children: [] }]), provideHttpClient(), provideHttpClientTesting(), { provide: ApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(SetupComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fill(token = '') {
    component.username = 'kafkador_admin';
    component.password = component.confirmPassword = 'a-long-password';
    component.setupToken = token;
  }

  it('asks for the setup token from the log when the server wants one, and sends it', async () => {
    await create(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('#setupToken')).not.toBeNull();

    fill(' 0123abcd ');
    component.submit();

    expect(api.configureDatabase).toHaveBeenCalledWith({ username: 'kafkador_admin', password: 'a-long-password', setupToken: '0123abcd' });
  });

  it('leaves the token out when the server does not ask for one', async () => {
    await create(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('#setupToken')).toBeNull();

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
});
