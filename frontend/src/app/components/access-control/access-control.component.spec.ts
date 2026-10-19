import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AccessControlComponent, PRINCIPAL_PATTERN } from './access-control.component';
import { ApiService, CommonService } from '../../services';

describe('AccessControlComponent', () => {
  let component: AccessControlComponent;
  let fixture: ComponentFixture<AccessControlComponent>;
  let api: jasmine.SpyObj<ApiService>;

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getAclBindings', 'createAclBinding', 'updateAclBinding']);
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

  it('chooses the pattern and permission type with switch buttons on one row', async () => {
    const element = fixture.nativeElement as HTMLElement;
    const prefixed = element.querySelector<HTMLInputElement>('#aclPatternTypePrefixed')!;
    const deny = element.querySelector<HTMLInputElement>('#aclPermissionTypeDeny')!;
    expect(prefixed.closest('.row')).toBe(deny.closest('.row'));

    prefixed.click();
    deny.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.newBinding.patternType).toBe('PREFIXED');
    expect(component.newBinding.permissionType).toBe('DENY');
  });

  it('exports the listed bindings', () => {
    const binding = { resourceType: 'TOPIC', resourceName: 'Events', patternType: 'LITERAL', principal: 'User:*',
      host: '*', operation: 'ALL', permissionType: 'DENY' };
    api.getAclBindings.and.returnValue(of({ body: { data: [binding] } } as any));
    component.loadBindings();
    expect(component.filteredBindings.length).toBe(1);

    const csv = component.exportContent('csv', component.filteredBindings).split('\r\n');
    expect(csv[0]).toBe('Resource type,Resource name,Pattern,Principal,Host,Operation,Permission');
    expect(csv[1]).toBe('TOPIC,Events,LITERAL,User:*,*,ALL,DENY');
  });

  it('puts the search on the left, Export and New ACL Binding on the right, and has no Refresh button', () => {
    const toolbar = (fixture.nativeElement as HTMLElement).querySelector('#acl-filtering-search')!.parentElement!;
    const buttons = Array.from(toolbar.querySelectorAll(':scope > button, :scope > .dropdown > button'));
    const position = (el: Element) => Array.from(toolbar.querySelectorAll('*')).indexOf(el);
    const search = toolbar.querySelector('#acl-filtering-search')!;
    const exportButton = buttons.find(b => b.textContent?.trim() === 'Export')!;
    const create = buttons.find(b => b.textContent?.includes('New ACL Binding'))!;
    expect(buttons.some(b => b.getAttribute('aria-label') === 'Refresh')).toBeFalse();
    expect(position(search)).toBeLessThan(position(exportButton));
    expect(position(exportButton)).toBeLessThan(position(create));
    expect(exportButton.parentElement!.classList).toContain('ms-auto');
  });

  it('edits a binding in place of the original', () => {
    const original = { resourceType: 'TOPIC', resourceName: 'Events', patternType: 'LITERAL', principal: 'User:*',
      host: '*', operation: 'ALL', permissionType: 'DENY' };
    const other = { ...original, resourceName: 'orders' };
    component.bindings = [original, other];
    api.updateAclBinding.and.returnValue(of({ body: { data: null } } as any));

    component.openEditDialog(original);
    expect(component.newBinding).toEqual(original);
    expect(component.newBinding).not.toBe(original);
    component.newBinding.permissionType = 'ALLOW';
    component.saveAclBinding();

    expect(api.updateAclBinding).toHaveBeenCalledWith(original, jasmine.objectContaining({ permissionType: 'ALLOW' }));
    expect(api.createAclBinding).not.toHaveBeenCalled();
    expect(component.bindings.map(b => b.permissionType + ' ' + b.resourceName)).toEqual(['ALLOW Events', 'DENY orders']);
    expect(component.editedBinding).toBeUndefined();
  });

  it('keeps the original listed when the edit fails', () => {
    const original = { resourceType: 'TOPIC', resourceName: 'Events', patternType: 'LITERAL', principal: 'User:*',
      host: '*', operation: 'ALL', permissionType: 'DENY' };
    component.bindings = [original];
    api.updateAclBinding.and.returnValue(throwError(() => ({ status: 400,
      error: { error: { code: '400', message: 'Invalid binding', datetime: '' } } })));

    component.openEditDialog(original);
    component.saveAclBinding();

    expect(component.bindings).toEqual([original]);
    expect(component.errors.get('createAclBinding')?.message).toContain('Invalid binding');
  });

  it('starts a new binding blank after editing one', () => {
    component.openEditDialog({ resourceType: 'GROUP', resourceName: 'g', patternType: 'LITERAL', principal: 'User:a',
      host: '*', operation: 'READ', permissionType: 'ALLOW' });
    component.openCreateDialog();

    expect(component.editedBinding).toBeUndefined();
    expect(component.newBinding.resourceName).toBe('');
  });
});
