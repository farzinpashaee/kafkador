import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { SchemaRegistryComponent } from './schema-registry.component';

describe('SchemaRegistryComponent', () => {
  let component: SchemaRegistryComponent;
  let fixture: ComponentFixture<SchemaRegistryComponent>;
  let navigate: jasmine.Spy;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchemaRegistryComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    })
    .compileComponents();

    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(SchemaRegistryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('opens a subject when its row is clicked', () => {
    const cell = document.createElement('td');
    component.openSubject('Customer', { target: cell } as unknown as MouseEvent);

    expect(navigate).toHaveBeenCalledWith(['/subject', 'Customer']);
  });

  it('leaves clicks on the row\'s link and buttons to them', () => {
    const button = document.createElement('button');
    button.appendChild(document.createElement('i'));
    component.openSubject('Customer', { target: button.firstChild } as unknown as MouseEvent);
    component.openSubject('Customer', { target: document.createElement('a') } as unknown as MouseEvent);

    expect(navigate).not.toHaveBeenCalled();
  });
});
