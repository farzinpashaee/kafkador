import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { LoginComponent } from './login.component';
import { ApiService } from '../../services';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let api: jasmine.SpyObj<ApiService>;
  let router: Router;

  async function create(status: object) {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getAccessStatus', 'login']);
    api.getAccessStatus.and.returnValue(of({ body: { data: status } } as any));
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ApiService, useValue: api }]
    }).compileComponents();
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('signs in and goes on', async () => {
    await create({ mode: 'DATABASE', loginRequired: true, authenticated: false });
    api.login.and.returnValue(of({ body: { data: { authenticated: true, username: 'admin' } } } as any));

    component.username = ' admin ';
    component.password = 'a-long-password';
    component.submit();

    expect(api.login).toHaveBeenCalledWith({ username: 'admin', password: 'a-long-password' });
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('says why signing in failed and clears the password', async () => {
    await create({ mode: 'LDAP', loginRequired: true, authenticated: false });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('LDAP');
    api.login.and.returnValue(throwError(() => ({ status: 401,
      error: { error: { code: '401', message: 'Wrong username or password.', datetime: '' } } })));

    component.username = 'alice';
    component.password = 'wrong';
    component.submit();

    expect(component.errors.get('login')?.message).toBe('Wrong username or password.');
    expect(component.password).toBe('');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('needs both fields', async () => {
    await create({ mode: 'DATABASE', loginRequired: true, authenticated: false });

    component.submit();

    expect(api.login).not.toHaveBeenCalled();
    expect(component.errors.get('login')).toBeDefined();
  });

  it('skips the page when there is no need to sign in', async () => {
    await create({ mode: 'NONE', loginRequired: false, authenticated: false });

    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
