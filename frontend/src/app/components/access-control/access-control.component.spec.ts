import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AccessControlComponent, PRINCIPAL_PATTERN } from './access-control.component';
import { ApiService, CommonService } from '../../services';

describe('AccessControlComponent', () => {
  let component: AccessControlComponent;
  let fixture: ComponentFixture<AccessControlComponent>;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getAclBindings', 'createAclBinding']);
    api.getAclBindings.and.returnValue(of({ body: { data: [] } } as any));
    api.createAclBinding.and.returnValue(of({ body: { data: null } } as any));

    await TestBed.configureTestingModule({
      imports: [AccessControlComponent],
      providers: [{ provide: ApiService, useValue: api }]
    })
    .compileComponents();

    // Bootstrap's modal API isn't loaded in unit tests.
    spyOn(TestBed.inject(CommonService), 'hideModal');
    fixture = TestBed.createComponent(AccessControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('accepts only Type:name principals', () => {
    expect(PRINCIPAL_PATTERN.test('User:alice')).toBeTrue();
    expect(PRINCIPAL_PATTERN.test('User:*')).toBeTrue();
    expect(PRINCIPAL_PATTERN.test('User:CN=alice,OU=dev')).toBeTrue();
    expect(PRINCIPAL_PATTERN.test('Farzin')).toBeFalse();
    expect(PRINCIPAL_PATTERN.test('User:')).toBeFalse();
    expect(PRINCIPAL_PATTERN.test(':alice')).toBeFalse();
  });

  it('stops a principal without its type and suggests the User: form', () => {
    component.newBinding = { ...component.newBinding, resourceName: 'ORDERS', principal: 'Farzin' };
    component.createAclBinding();
    expect(api.createAclBinding).not.toHaveBeenCalled();
    expect(component.errors.get('createAclBinding')?.message).toContain('"User:Farzin"');
  });

  it('creates a binding with a valid principal', () => {
    component.newBinding = { ...component.newBinding, resourceName: 'ORDERS', principal: 'User:Farzin' };
    component.createAclBinding();
    expect(api.createAclBinding).toHaveBeenCalled();
    expect(component.errors.get('createAclBinding')).toBeUndefined();
  });

  it('shows the reason the cluster gives for rejecting a binding', () => {
    api.createAclBinding.and.returnValue(throwError(() => ({ status: 400,
      error: { error: { code: '400', message: 'Could not parse principal from `x`' } } })));
    component.newBinding = { ...component.newBinding, resourceName: 'ORDERS', principal: 'User:x' };
    component.createAclBinding();
    expect(component.errors.get('createAclBinding')?.message).toContain('Could not parse principal');
  });

  it('refreshes the bindings in place and exports the listed ones', () => {
    const binding = { resourceType: 'TOPIC', resourceName: 'Events', patternType: 'LITERAL', principal: 'User:*',
      host: '*', operation: 'ALL', permissionType: 'DENY' };
    api.getAclBindings.and.returnValue(of({ body: { data: [binding] } } as any));
    component.loadBindings();
    expect(component.filteredBindings.length).toBe(1);
    expect(component.flags.get('refreshingAclBindings')).toBeFalse();

    const csv = component.exportContent('csv', component.filteredBindings).split('\r\n');
    expect(csv[0]).toBe('Resource type,Resource name,Pattern,Principal,Host,Operation,Permission');
    expect(csv[1]).toBe('TOPIC,Events,LITERAL,User:*,*,ALL,DENY');
  });

  it('puts the search and Refresh on the left, Export and New ACL Binding on the right', () => {
    const toolbar = (fixture.nativeElement as HTMLElement).querySelector('#acl-filtering-search')!.parentElement!;
    const buttons = Array.from(toolbar.querySelectorAll(':scope > button, :scope > .dropdown > button'));
    const position = (el: Element) => Array.from(toolbar.querySelectorAll('*')).indexOf(el);
    const search = toolbar.querySelector('#acl-filtering-search')!;
    const exportButton = buttons.find(b => b.textContent?.trim() === 'Export')!;
    const refresh = buttons.find(b => b.getAttribute('aria-label') === 'Refresh')!;
    const create = buttons.find(b => b.textContent?.includes('New ACL Binding'))!;
    expect(position(search)).toBeLessThan(position(refresh));
    expect(position(refresh)).toBeLessThan(position(exportButton));
    expect(position(exportButton)).toBeLessThan(position(create));
    expect(exportButton.parentElement!.classList).toContain('ms-auto');
  });
});
