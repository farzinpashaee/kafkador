import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { SubjectComponent } from './subject.component';

describe('SubjectComponent', () => {
  let component: SubjectComponent;
  let fixture: ComponentFixture<SubjectComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubjectComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'test-subject' } } }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SubjectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('indents a JSON or Avro schema and leaves Protobuf alone', () => {
    const json = { subject: 'test-subject', version: 1, id: 1, schemaType: 'JSON', schema: '{"type":"object","properties":{"a":{"type":"string"}}}' } as any;

    expect(component.formattedSchema(json)).toBe('{\n  "type": "object",\n  "properties": {\n    "a": {\n      "type": "string"\n    }\n  }\n}');
    expect(component.formattedSchema({ ...json, schemaType: undefined, schema: '{"type":"record","name":"C","fields":[]}' })).toContain('\n  "name": "C"');
    expect(component.formattedSchema({ ...json, schemaType: 'PROTOBUF', schema: 'syntax = "proto3";' })).toBeNull();
    expect(component.formattedSchema({ ...json, schema: 'not json' })).toBeNull();
  });

  it('switches a version between formatted and as registered', () => {
    component.flags.set('versionsLoading', false);
    component.versions = [1];
    component.expandedVersion = 1;
    component.versionDetails.set(1, { subject: 'test-subject', version: 1, id: 1, schemaType: 'JSON', schema: '{"a":1}' } as any);
    fixture.detectChanges();
    const code = () => (fixture.nativeElement as HTMLElement).querySelector('pre code')!.textContent;
    expect(code()).toBe('{"a":1}');

    component.toggleFormatted(1);
    fixture.detectChanges();
    expect(code()).toBe('{\n  "a": 1\n}');
    expect(component.expandedVersion).toBe(1);

    component.toggleFormatted(1);
    fixture.detectChanges();
    expect(code()).toBe('{"a":1}');
  });
});
