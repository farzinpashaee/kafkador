import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { ApiService, BookmarkService, CommonService, LocalStorageService } from '../../services';
import { BookmarkEntityType, GenericResponse, KsqlDbConfig, KsqlServerInfo, KsqlStream, KsqlTable, KsqlQuery, Error } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport } from '../../services/table-export';

export type KsqlTab = 'streams' | 'tables' | 'queries';

/** A column the Filter menu narrows by; rows without a value fall under NO_VALUE. */
export interface KsqlFacet<T> {
  key: string;
  label: string;
  value: (row: T) => string | undefined;
}

export const NO_VALUE = '(none)';

const BOOKMARK_TYPES: Record<KsqlTab, BookmarkEntityType> = {
  streams: 'KSQL_STREAM',
  tables: 'KSQL_TABLE',
  queries: 'KSQL_QUERY'
};

const BOOKMARK_NOUNS: Record<KsqlTab, string> = { streams: 'streams', tables: 'tables', queries: 'queries' };

const bookmarkedOnlyStorageKey = (tab: KsqlTab) => `ksqldb.${tab}.bookmarkedOnly`;

type KsqlSource = KsqlStream | KsqlTable;

const SOURCE_FACETS: KsqlFacet<KsqlSource>[] = [
  { key: 'keyFormat', label: 'Key format', value: s => s.keyFormat },
  { key: 'valueFormat', label: 'Value format', value: s => s.valueFormat }
];

const QUERY_FACETS: KsqlFacet<KsqlQuery>[] = [
  { key: 'queryType', label: 'Type', value: q => q.queryType },
  { key: 'status', label: 'Status', value: q => q.status }
];

const SOURCE_EXPORT_FIELDS: ExportField<KsqlSource>[] = [
  { key: 'name', label: 'Name', value: s => s.name },
  { key: 'topic', label: 'Topic', value: s => s.topic },
  { key: 'keyFormat', label: 'Key format', value: s => s.keyFormat },
  { key: 'valueFormat', label: 'Value format', value: s => s.valueFormat }
];

const QUERY_EXPORT_FIELDS: ExportField<KsqlQuery>[] = [
  { key: 'id', label: 'ID', value: q => q.id },
  { key: 'type', label: 'Type', value: q => q.queryType },
  { key: 'status', label: 'Status', value: q => q.status },
  { key: 'sources', label: 'Sources', value: q => q.sources?.join(';') },
  { key: 'sinks', label: 'Sinks', value: q => q.sinks?.join(';') }
];

@Component({
  selector: 'app-ksql-db',
  imports: [CommonModule, FormsModule, RouterLink, PaginationComponent],
  templateUrl: './ksql-db.component.html',
  styleUrl: './ksql-db.component.scss'
})
export class KsqlDBComponent implements OnInit {

  config: KsqlDbConfig = { configured: false };
  serverInfo!: KsqlServerInfo;
  streams: KsqlStream[] = [];
  tables: KsqlTable[] = [];
  queries: KsqlQuery[] = [];
  terminatedQuery: KsqlQuery = { id: '', queryType: '' };

  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  readonly pageSize = 10;
  streamsPage = 1;
  tablesPage = 1;
  queriesPage = 1;

  readonly exportFormats = EXPORT_FORMATS;
  readonly sourceFacets = SOURCE_FACETS;
  readonly queryFacets = QUERY_FACETS;
  searchTerms: Record<KsqlTab, string> = { streams: '', tables: '', queries: '' };
  /** Per tab and facet, the values ticked in the Filter menu; none ticked means no filtering on that facet. */
  selectedValues: Record<KsqlTab, Map<string, Set<string>>> = { streams: new Map(), tables: new Map(), queries: new Map() };
  /** Per tab, whether only bookmarked rows are listed; remembered across visits. */
  bookmarkedOnly: Record<KsqlTab, boolean> = { streams: false, tables: false, queries: false };

  private bookmarkService = inject(BookmarkService);
  private localStorageService = inject(LocalStorageService);
  /** Bookmarked stream and table names and query IDs, by tab. */
  readonly bookmarks = {
    streams: this.bookmarkService.bookmarks('KSQL_STREAM'),
    tables: this.bookmarkService.bookmarks('KSQL_TABLE'),
    queries: this.bookmarkService.bookmarks('KSQL_QUERY')
  };

  /** Streams matching the search box (name or topic) and the format and bookmark filters. */
  get filteredStreams(): KsqlStream[] {
    return this.filterRows('streams', this.streams, SOURCE_FACETS, s => s.name, s => [s.name, s.topic]);
  }

  get filteredTables(): KsqlTable[] {
    return this.filterRows('tables', this.tables, SOURCE_FACETS, t => t.name, t => [t.name, t.topic]);
  }

  /** Queries matching the search box (ID, sources or sinks) and the type, status and bookmark filters. */
  get filteredQueries(): KsqlQuery[] {
    return this.filterRows('queries', this.queries, QUERY_FACETS, q => q.id,
      q => [q.id, ...(q.sources ?? []), ...(q.sinks ?? [])]);
  }

  get pagedStreams(): KsqlStream[] {
    return this.filteredStreams.slice((this.streamsPage - 1) * this.pageSize, this.streamsPage * this.pageSize);
  }

  get pagedTables(): KsqlTable[] {
    return this.filteredTables.slice((this.tablesPage - 1) * this.pageSize, this.tablesPage * this.pageSize);
  }

  get pagedQueries(): KsqlQuery[] {
    return this.filteredQueries.slice((this.queriesPage - 1) * this.pageSize, this.queriesPage * this.pageSize);
  }

  /** The values a facet takes across all of a tab's rows, with how many rows have each, for the Filter menu. */
  facetOptions(tab: KsqlTab, facet: KsqlFacet<any>): { value: string; count: number }[] {
    const counts = new Map<string, number>();
    for (const row of this.rowsOf(tab)) {
      const value = facet.value(row) || NO_VALUE;
      counts.set(value, (counts.get(value) ?? 0) + 1);
    }
    return Array.from(counts, ([value, count]) => ({ value, count })).sort((a, b) => a.value.localeCompare(b.value));
  }

  isSelected(tab: KsqlTab, facetKey: string, value: string): boolean {
    return this.selectedValues[tab].get(facetKey)?.has(value) ?? false;
  }

  toggleFacet(tab: KsqlTab, facetKey: string, value: string, checked: boolean): void {
    const selected = this.selectedValues[tab].get(facetKey) ?? new Set<string>();
    if (checked) selected.add(value); else selected.delete(value);
    this.selectedValues[tab].set(facetKey, selected);
    this.resetPage(tab);
  }

  activeFilterCount(tab: KsqlTab): number {
    let count = this.bookmarkedOnly[tab] ? 1 : 0;
    this.selectedValues[tab].forEach(values => count += values.size);
    return count;
  }

  clearFilters(tab: KsqlTab): void {
    this.selectedValues[tab].clear();
    this.setBookmarkedOnly(tab, false);
    this.resetPage(tab);
  }

  isBookmarked(tab: KsqlTab, id: string): boolean {
    return this.bookmarks[tab]().has(id);
  }

  bookmarkCount(tab: KsqlTab): number {
    return this.bookmarks[tab]().size;
  }

  toggleBookmark(tab: KsqlTab, id: string): void {
    this.errors.delete('bookmarks');
    this.bookmarkService.toggle(BOOKMARK_TYPES[tab], id, res => this.bookmarkError(res, 'Failed to update the bookmark!'));
  }

  isBookmarkedOnly(tab: KsqlTab): boolean {
    return this.bookmarkedOnly[tab];
  }

  setBookmarkedOnly(tab: KsqlTab, only: boolean): void {
    this.bookmarkedOnly[tab] = only;
    this.resetPage(tab);
    this.localStorageService.setItem(bookmarkedOnlyStorageKey(tab), only);
  }

  searchTermOf(tab: KsqlTab): string {
    return this.searchTerms[tab];
  }

  setSearchTerm(tab: KsqlTab, term: string): void {
    this.searchTerms[tab] = term;
    this.resetPage(tab);
  }

  /** Exports a tab's rows as currently listed (search and filters applied, all pages). */
  export(tab: KsqlTab, format: ExportFormat): void {
    downloadExport(this.exportContent(tab, format), format, `ksqldb-${tab}`);
  }

  exportContent(tab: KsqlTab, format: ExportFormat): string {
    switch (tab) {
      case 'streams': return buildExport(format, SOURCE_EXPORT_FIELDS, this.filteredStreams, 'streams', 'stream');
      case 'tables': return buildExport(format, SOURCE_EXPORT_FIELDS, this.filteredTables, 'tables', 'table');
      case 'queries': return buildExport(format, QUERY_EXPORT_FIELDS, this.filteredQueries, 'queries', 'query');
    }
  }

  private rowsOf(tab: KsqlTab): (KsqlSource | KsqlQuery)[] {
    return tab === 'streams' ? this.streams : tab === 'tables' ? this.tables : this.queries;
  }

  private bookmarkError(res: HttpErrorResponse, message: string): void {
    this.errors.set('bookmarks', this.commonService.prepareError(res.error?.error, '500', message));
  }

  private filterRows<T>(tab: KsqlTab, rows: T[], facets: KsqlFacet<T>[], id: (row: T) => string,
                        searchable: (row: T) => (string | undefined)[]): T[] {
    const term = this.searchTerms[tab].trim().toLowerCase();
    const selected = this.selectedValues[tab];
    const bookmarks = this.bookmarks[tab]();
    return rows.filter(row =>
      (!this.bookmarkedOnly[tab] || bookmarks.has(id(row)))
      && (!term || searchable(row).some(text => text?.toLowerCase().includes(term)))
      && facets.every(facet => {
        const values = selected.get(facet.key);
        return !values?.size || values.has(facet.value(row) || NO_VALUE);
      }));
  }

  private resetPage(tab: KsqlTab): void {
    if (tab === 'streams') this.streamsPage = 1;
    else if (tab === 'tables') this.tablesPage = 1;
    else this.queriesPage = 1;
  }

  constructor(private apiService: ApiService, private commonService: CommonService) {}

  ngOnInit() {
    for (const tab of Object.keys(BOOKMARK_TYPES) as KsqlTab[]) {
      this.bookmarkedOnly[tab] = this.localStorageService.getItem<boolean>(bookmarkedOnlyStorageKey(tab)) ?? false;
    }
    this.getConfig();
  }

  getConfig() {
    this.errors.delete('getConfig');
    this.flags.set('configLoading', true);
    this.apiService.getKsqlDbConfig().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlDbConfig>>) => {
        this.config = res.body?.data ?? { configured: false };
        this.flags.set('configLoading', false);
        if (this.config.configured) this.loadData();
      },
      error: (res:HttpErrorResponse) => {
        if (res.status === 404) {
          this.config = { configured: false };
        } else {
          this.errors.set('getConfig', this.commonService.prepareError(res.error.error,'500','Failed to load ksqlDB configuration!'));
        }
        this.flags.set('configLoading', false);
      }
    });
  }

  loadData() {
    this.errors.delete('loadData');
    this.flags.set('dataLoading', true);

    for (const tab of Object.keys(BOOKMARK_TYPES) as KsqlTab[]) {
      this.bookmarkService.load(BOOKMARK_TYPES[tab],
        res => this.bookmarkError(res, `Failed to get bookmarked ${BOOKMARK_NOUNS[tab]}!`));
    }

    this.apiService.getKsqlDbInfo().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlServerInfo>>) => { this.serverInfo = res.body!.data; },
      error: (res:HttpErrorResponse) => {
        this.errors.set('loadData', this.commonService.prepareError(res.error.error,'500','Failed to reach the ksqlDB server!'));
      }
    });

    this.apiService.getKsqlDbStreams().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlStream[]>>) => {
        this.streams = res.body?.data ?? [];
        this.flags.set('dataLoading', false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('loadData', this.commonService.prepareError(res.error.error,'500','Failed to reach the ksqlDB server!'));
        this.flags.set('dataLoading', false);
      }
    });

    this.apiService.getKsqlDbTables().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlTable[]>>) => { this.tables = res.body?.data ?? []; },
      error: () => {}
    });

    this.apiService.getKsqlDbQueries().subscribe({
      next: (res: HttpResponse<GenericResponse<KsqlQuery[]>>) => { this.queries = res.body?.data ?? []; },
      error: () => {}
    });
  }

  openTerminateDialog(query: KsqlQuery) {
    this.errors.delete('terminateQuery');
    this.terminatedQuery = query;
  }

  terminateQuery() {
    if (!this.terminatedQuery) return;
    this.flags.set('terminateQueryLoading', true);
    this.apiService.terminateKsqlDbQuery(this.terminatedQuery.id).subscribe({
      next: () => {
        this.queries = this.queries.filter(q => q.id !== this.terminatedQuery.id);
        this.queriesPage = Math.min(this.queriesPage, Math.max(1, Math.ceil(this.filteredQueries.length / this.pageSize)));
        this.flags.set('terminateQueryLoading', false);
        this.commonService.hideModal('terminateQueryModal');
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set('terminateQuery', this.commonService.prepareError(res.error.error,'500','Failed to terminate query!'));
        this.flags.set('terminateQueryLoading', false);
      }
    });
  }

}
