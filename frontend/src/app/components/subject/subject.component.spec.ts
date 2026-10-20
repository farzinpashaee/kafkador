import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SubjectComponent, diffLines } from './subject.component';

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

  it('indents the schema being registered, and explains when it is not JSON', () => {
    component.newSchema = '{"type":"object","properties":{}}';
    component.formatNewSchema();
    expect(component.newSchema).toBe('{\n  "type": "object",\n  "properties": {}\n}');
    expect(component.errors.get('register')).toBeUndefined();

    component.newSchema = '{"type":';
    component.formatNewSchema();
    expect(component.newSchema).toBe('{"type":');
    expect(component.errors.get('register')?.message).toContain("isn't valid JSON");
  });

  it('diffs by alignment, so an inserted line is one added row', () => {
    const rows = diffLines('{\n  "a": 1,\n  "c": 3\n}', '{\n  "a": 1,\n  "b": 2,\n  "c": 3\n}');

    expect(rows.map(r => r.kind)).toEqual(['same', 'same', 'added', 'same', 'same']);
    expect(rows[2]).toEqual(jasmine.objectContaining({ right: '  "b": 2,', rightNumber: 3, left: undefined }));
    expect(rows[3]).toEqual(jasmine.objectContaining({ leftNumber: 3, rightNumber: 4 }));
  });

  it('pairs removed and added lines next to each other as changed', () => {
    const rows = diffLines('x\nold\ny\ngone', 'x\nnew\ny');

    expect(rows.map(r => [r.kind, r.left, r.right])).toEqual([
      ['same', 'x', 'x'], ['changed', 'old', 'new'], ['same', 'y', 'y'], ['removed', 'gone', undefined]
    ]);
  });

  it('indents both versions before comparing them', () => {
    const http = TestBed.inject(HttpTestingController);
    http.match(() => true);
    component.compareLeftVersion = 1;
    component.compareRightVersion = 2;
    component.showDiff();
    const answer = (version: number, schema: string) => http.match(r => r.url.endsWith(`/versions/${version}`))
      .forEach(r => r.flush({ data: { subject: 'test-subject', version, id: version, schemaType: 'JSON', schema } }));
    answer(1, '{"a":1,"c":3}');
    answer(2, '{"a":1,"b":2,"c":3}');

    expect(component.diffRows.filter(r => r.kind !== 'same').map(r => r.right)).toEqual(['  "b": 2,']);
    expect(component.diffAdded).toBe(1);
    expect(component.diffRemoved).toBe(0);
  });
});
