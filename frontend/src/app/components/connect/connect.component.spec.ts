import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ConnectComponent } from './connect.component';
import { ApiService, CommonService } from '../../services';
import { Connection, CREDENTIALS_REQUIRED } from '../../models';

describe('ConnectComponent', () => {
  let component: ConnectComponent;
  let fixture: ComponentFixture<ConnectComponent>;
  let api: jasmine.SpyObj<ApiService>;
  let common: CommonService;

  const prod: Connection = { id: '7', clusterId: 'c1', name: 'prod', host: 'kafka', port: '9093' };
  const credentialsRequired = (message: string) => throwError(() => ({ status: 403,
    error: { error: { code: CREDENTIALS_REQUIRED, message } } }));

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getConnections', 'connect', 'addConnection', 'getAccessStatus', 'logout']);
    api.getConnections.and.returnValue(of({ body: { data: [prod] } } as any));
    api.getAccessStatus.and.returnValue(of({ body: { data: { mode: 'DATABASE', loginRequired: true, authenticated: true, username: 'admin' } } } as any));

    await TestBed.configureTestingModule({
      imports: [ConnectComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: ApiService, useValue: api }]
    })
    .compileComponents();

    // Bootstrap's modal API isn't loaded in unit tests.
    common = TestBed.inject(CommonService);
    spyOn(common, 'showModal');
    spyOn(common, 'hideModal');
    spyOn(TestBed.inject(Router), 'navigate');
    fixture = TestBed.createComponent(ConnectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('connects straight away when the cluster needs no credentials', () => {
    api.connect.and.returnValue(of({ data: prod } as any));
    component.connect(prod);
    expect(api.connect).toHaveBeenCalledWith('7');
    expect(common.showModal).not.toHaveBeenCalled();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/']);
  });

  it('asks for credentials when the cluster requires them, then connects with them', () => {
    api.connect.and.returnValue(credentialsRequired('The cluster requires credentials'));
    component.connect(prod);
    expect(common.showModal).toHaveBeenCalledWith('clusterSignInModal');
    expect(component.signInMessage).toBe('The cluster requires credentials');

    api.connect.and.returnValue(of({ data: { ...prod, authenticated: true } } as any));
    component.signInCredentials = { securityProtocol: 'SASL_PLAINTEXT', saslMechanism: 'SCRAM_SHA_512', username: 'farzin', password: 'pw' };
    component.signIn();
    expect(api.connect).toHaveBeenCalledWith('7', jasmine.objectContaining({ username: 'farzin', saslMechanism: 'SCRAM_SHA_512' }));
    expect(common.hideModal).toHaveBeenCalledWith('clusterSignInModal');
    // The password isn't kept around in the component once connected.
    expect(component.signInCredentials.password).toBe('');
  });

  it('keeps the dialog open and says why when the credentials are rejected', () => {
    api.connect.and.returnValue(credentialsRequired('The cluster rejected these credentials: Invalid username or password'));
    component.connectingTo = prod;
    component.signInCredentials = { securityProtocol: 'SASL_PLAINTEXT', saslMechanism: 'PLAIN', username: 'farzin', password: 'nope' };
    component.signIn();
    expect(component.errors.get('signIn')?.message).toContain('Invalid username or password');
    expect(common.hideModal).not.toHaveBeenCalled();
  });

  it('needs a username and password before signing in', () => {
    component.connectingTo = prod;
    component.signIn();
    expect(api.connect).not.toHaveBeenCalled();
    expect(component.errors.get('signIn')).toBeTruthy();
  });

  it('shows other connection failures on the page', () => {
    api.connect.and.returnValue(throwError(() => ({ status: 404, error: { error: { message: 'Connection with given cluster ID not found!' } } })));
    component.connect(prod);
    expect(component.errors.get('connect')?.message).toContain('not found');
    expect(component.connectingTo).toBeUndefined();
  });

  it('turns on the authentication fields when adding a cluster that requires credentials', () => {
    api.addConnection.and.returnValue(credentialsRequired('The cluster requires credentials'));
    component.newConnection = { id: '', clusterId: '', name: 'prod', host: 'kafka', port: '9093' };
    component.addConnection();
    expect(component.newConnectionAuth).toBeTrue();

    api.addConnection.calls.reset();
    api.addConnection.and.returnValue(of({ body: { data: prod } } as any));
    component.newCredentials = { securityProtocol: 'SASL_SSL', saslMechanism: 'PLAIN', username: 'farzin', password: 'pw' };
    component.addConnection();
    expect(api.addConnection.calls.mostRecent().args[0].credentials?.securityProtocol).toBe('SASL_SSL');
  });
});
