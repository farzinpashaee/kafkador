import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { KsqlDBComponent } from './ksql-db.component';
import { ApiService, CommonService, LocalStorageService } from '../../services';
import { KsqlQuery, KsqlStream, KsqlTable } from '../../models';

describe('KsqlDBComponent', () => {
  let component: KsqlDBComponent;
  let fixture: ComponentFixture<KsqlDBComponent>;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;
  let storage: Map<string, unknown>;

  const streams: KsqlStream[] = [
    { name: 'ORDERPQ', topic: 'ORDERPQ', keyFormat: 'KAFKA', valueFormat: 'JSON' },
    { name: 'KSQL_PROCESSING_LOG', topic: 'default_ksql_processing_log', keyFormat: 'KAFKA', valueFormat: 'JSON' },
    { name: 'PAYMENTS', topic: 'payments', keyFormat: 'JSON', valueFormat: 'AVRO' }
  ];
  const tables: KsqlTable[] = [
    { name: 'ORDER_TOTALS', topic: 'ORDER_TOTALS', keyFormat: 'KAFKA', valueFormat: 'AVRO' }
  ];
  const queries: KsqlQuery[] = [
    { id: 'CSAS_ORDERPQ_1', queryType: 'PERSISTENT', status: 'RUNNING', sources: ['ORDERS'], sinks: ['ORDERPQ'] },
    { id: 'CTAS_TOTALS_3', queryType: 'PERSISTENT', status: 'ERROR', sources: ['ORDERPQ'], sinks: ['ORDER_TOTALS'] }
  ];

  function response<T>(data: T): HttpResponse<any> {
    return new HttpResponse({ body: { data } });
  }

  beforeEach(async () => {
    storage = new Map();
    apiServiceSpy = jasmine.createSpyObj('ApiService',
      ['getKsqlDbConfig', 'getKsqlDbInfo', 'getKsqlDbStreams', 'getKsqlDbTables', 'getKsqlDbQueries', 'terminateKsqlDbQuery',
        'getBookmarks', 'addBookmark', 'removeBookmark']);
    apiServiceSpy.getBookmarks.and.callFake((type: string) => of({
      data: type === 'KSQL_STREAM' ? [{ entityType: 'KSQL_STREAM', entityId: 'PAYMENTS' }]
        : type === 'KSQL_QUERY' ? [{ entityType: 'KSQL_QUERY', entityId: 'CTAS_TOTALS_3' }] : []
    } as any));
    apiServiceSpy.addBookmark.and.returnValue(of({} as any));
    apiServiceSpy.removeBookmark.and.returnValue(of(undefined));
    apiServiceSpy.getKsqlDbConfig.and.returnValue(of(response({ configured: true, url: 'http://ksql:8088' })));
    apiServiceSpy.getKsqlDbInfo.and.returnValue(of(response({ version: '7.6', kafkaClusterId: 'c', ksqlServiceId: 's', serverStatus: 'RUNNING' })));
    apiServiceSpy.getKsqlDbStreams.and.returnValue(of(response(streams)));
    apiServiceSpy.getKsqlDbTables.and.returnValue(of(response(tables)));
    apiServiceSpy.getKsqlDbQueries.and.returnValue(of(response(queries)));

    await TestBed.configureTestingModule({
      imports: [KsqlDBComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceSpy },
        { provide: CommonService, useValue: new CommonService() },
        {
          provide: LocalStorageService,
          useValue: { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: unknown) => storage.set(key, value) }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(KsqlDBComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows a search box, Filter and Export on every tab', () => {
    const element = fixture.nativeElement as HTMLElement;
    for (const tab of ['streams', 'tables', 'queries']) {
      const pane = element.querySelector(`#${tab}`)!;
      expect(pane.querySelector(`#${tab}-search`)).withContext(tab).not.toBeNull();
      const buttons = Array.from(pane.querySelectorAll('button')).map(b => b.textContent?.trim());
      expect(buttons).withContext(tab).toContain('Filter');
      expect(buttons).withContext(tab).toContain('Export');
    }
  });

  it('searches streams by name or topic', () => {
    component.searchTerms.streams = 'processing';
    expect(component.filteredStreams.map(s => s.name)).toEqual(['KSQL_PROCESSING_LOG']);

    component.searchTerms.streams = 'payments';
    expect(component.filteredStreams.map(s => s.name)).toEqual(['PAYMENTS']);
  });

  it('lists each format with its count and filters by key and value format', () => {
    expect(component.facetOptions('streams', component.sourceFacets[0]))
      .toEqual([{ value: 'JSON', count: 1 }, { value: 'KAFKA', count: 2 }]);

    component.toggleFacet('streams', 'keyFormat', 'KAFKA', true);
    expect(component.filteredStreams.length).toBe(2);

    component.toggleFacet('streams', 'valueFormat', 'AVRO', true);
    expect(component.filteredStreams).toEqual([]);
    expect(component.activeFilterCount('streams')).toBe(2);

    component.clearFilters('streams');
    expect(component.filteredStreams.length).toBe(3);
  });

  it('keeps each tab\'s filters separate', () => {
    component.toggleFacet('streams', 'valueFormat', 'AVRO', true);
    expect(component.filteredTables.length).toBe(1);
    expect(component.activeFilterCount('tables')).toBe(0);
  });

  it('filters queries by status and searches their sources and sinks', () => {
    component.toggleFacet('queries', 'status', 'ERROR', true);
    expect(component.filteredQueries.map(q => q.id)).toEqual(['CTAS_TOTALS_3']);

    component.clearFilters('queries');
    component.searchTerms.queries = 'order_totals';
    expect(component.filteredQueries.map(q => q.id)).toEqual(['CTAS_TOTALS_3']);
  });

  it('exports only the rows that match the search and filters', () => {
    component.toggleFacet('streams', 'valueFormat', 'AVRO', true);

    expect(component.exportContent('streams', 'csv'))
      .toBe('Name,Topic,Key format,Value format\r\nPAYMENTS,payments,JSON,AVRO');
    expect(JSON.parse(component.exportContent('queries', 'json'))[0])
      .toEqual({ id: 'CSAS_ORDERPQ_1', type: 'PERSISTENT', status: 'RUNNING', sources: 'ORDERS', sinks: 'ORDERPQ' });
  });

  it('loads the bookmarks of streams, tables and queries', () => {
    expect(apiServiceSpy.getBookmarks).toHaveBeenCalledWith('KSQL_STREAM');
    expect(apiServiceSpy.getBookmarks).toHaveBeenCalledWith('KSQL_TABLE');
    expect(apiServiceSpy.getBookmarks).toHaveBeenCalledWith('KSQL_QUERY');
    expect(component.isBookmarked('streams', 'PAYMENTS')).toBeTrue();
    expect(component.isBookmarked('queries', 'CTAS_TOTALS_3')).toBeTrue();
    expect(component.isBookmarked('tables', 'ORDER_TOTALS')).toBeFalse();
  });

  it('shows a bookmark button on every row', () => {
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('#streams [aria-label^="Bookmark stream"]').length).toBe(3);
    expect(element.querySelectorAll('#tables [aria-label^="Bookmark table"]').length).toBe(1);
    expect(element.querySelectorAll('#queries [aria-label^="Bookmark query"]').length).toBe(2);
    expect(element.querySelector('[aria-label="Bookmark stream PAYMENTS"]')!.getAttribute('aria-pressed')).toBe('true');
  });

  it('bookmarks a row under its own type', () => {
    component.toggleBookmark('tables', 'ORDER_TOTALS');
    expect(apiServiceSpy.addBookmark).toHaveBeenCalledWith({ entityType: 'KSQL_TABLE', entityId: 'ORDER_TOTALS' });
    expect(component.isBookmarked('tables', 'ORDER_TOTALS')).toBeTrue();

    component.toggleBookmark('streams', 'PAYMENTS');
    expect(apiServiceSpy.removeBookmark).toHaveBeenCalledWith({ entityType: 'KSQL_STREAM', entityId: 'PAYMENTS' });
    expect(component.isBookmarked('streams', 'PAYMENTS')).toBeFalse();
  });

  it('filters each tab to its bookmarked rows and remembers the choice', () => {
    component.setBookmarkedOnly('streams', true);
    expect(component.filteredStreams.map(s => s.name)).toEqual(['PAYMENTS']);
    expect(component.filteredQueries.length).toBe(2);
    expect(component.activeFilterCount('streams')).toBe(1);
    expect(storage.get('ksqldb.streams.bookmarkedOnly')).toBeTrue();

    component.setBookmarkedOnly('queries', true);
    expect(component.filteredQueries.map(q => q.id)).toEqual(['CTAS_TOTALS_3']);

    component.clearFilters('streams');
    expect(component.filteredStreams.length).toBe(3);
    expect(storage.get('ksqldb.streams.bookmarkedOnly')).toBeFalse();
  });

  it('goes back to the first page when the search changes', () => {
    component.streamsPage = 2;
    component.setSearchTerm('streams', 'ORDER');
    expect(component.streamsPage).toBe(1);
    expect(component.filteredStreams.map(s => s.name)).toEqual(['ORDERPQ']);
  });
});
