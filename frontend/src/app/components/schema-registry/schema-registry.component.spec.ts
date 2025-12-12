import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SchemaRegistryComponent } from './schema-registry.component';

describe('SchemaRegistryComponent', () => {
  let component: SchemaRegistryComponent;
  let fixture: ComponentFixture<SchemaRegistryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchemaRegistryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SchemaRegistryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
