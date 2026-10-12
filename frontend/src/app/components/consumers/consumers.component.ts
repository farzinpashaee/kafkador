import { Component, effect, inject } from '@angular/core';
import { ActivatedRoute,Router,RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { NgxChartsModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { combineLatest } from 'rxjs';
import { startWith, map, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ApiService, CommonService, LocalStorageService, BookmarkService } from '../../services';
import { ConsumerGroup, Chart, Error, Connection, GenericResponse } from '../../models';
import { PaginationComponent } from '../pagination/pagination.component';
import { EXPORT_FORMATS, ExportField, ExportFormat, buildExport, downloadExport } from '../../services/table-export';

const EXPORT_FIELDS: ExportField<ConsumerGroup>[] = [
  { key: 'id', label: 'ID', value: g => g.id },
  { key: 'type', label: 'Type', value: g => g.type },
  { key: 'coordinator', label: 'Coordinator', value: g => g.coordinator },
  { key: 'simpleConsumerGroup', label: 'Simple consumer group', value: g => g.isSimpleConsumerGroup },
  { key: 'partitionAssignor', label: 'Partition assignor', value: g => g.partitionAssignor },
  { key: 'groupState', label: 'Group state', value: g => g.groupState }
];

const BOOKMARKED_ONLY_STORAGE_KEY = 'consumers.bookmarkedOnly';

@Component({
  selector: 'app-consumers',
  imports: [CommonModule,RouterModule,NgxChartsModule,FormsModule,ReactiveFormsModule,PaginationComponent],
  templateUrl: './consumers.component.html',
  styleUrl: './consumers.component.scss'
})
export class ConsumersComponent {
  data = [
    { name: 'a1', value: 5000 },
    { name: 'a2', value: 3000 },
    { name: 'a3', value: 2000 },
    { name: 'a4', value: 3450 },
    { name: 'a5', value: 2400 }
  ];

  cartTopWidgetWhiteScheme: Color = {
    name: 'cartTopWidgetWhiteScheme',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: ['#FFF']
  };

  consumerGroups: ConsumerGroup[] = [];
  filteredConsumerGroups: ConsumerGroup[] = [];
  bookmarkedOnly = false;
  private bookmarkService = inject(BookmarkService);
  readonly bookmarks = this.bookmarkService.bookmarks('CONSUMER_GROUP');
  // Re-apply the bookmark filter when bookmarks load or one is toggled.
  private readonly bookmarkFilter = effect(() => {
    this.bookmarks();
    this.refilter();
  });
  activeConnection: Connection | null = null;
  errors: Map<string, Error> = new Map();
  flags: Map<string, boolean> = new Map();
  filter = new FormControl('', { nonNullable: true });
  readonly pageSize = 10;
  page = 1;
  readonly exportFormats = EXPORT_FORMATS;

  get pagedConsumerGroups(): ConsumerGroup[] {
    return this.filteredConsumerGroups.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  constructor(private apiService: ApiService,
    private commonService: CommonService,
    private localStorageService: LocalStorageService,
    private router: Router,
    private route: ActivatedRoute) {}

  ngOnInit() {
    this.activeConnection = this.localStorageService.getItem<Connection>("activeConnection");
    this.bookmarkedOnly = this.localStorageService.getItem<boolean>(BOOKMARKED_ONLY_STORAGE_KEY) ?? false;
    this.bookmarkService.load('CONSUMER_GROUP', res => this.bookmarkError(res, 'Failed to get bookmarked consumer groups!'));
    this.flags.set('getConsumerGroupsLoading',true);
    this.flags.set('agentEnabled',true);

    const searchQuery = this.route.snapshot.queryParamMap.get('q');
    if (searchQuery) this.filter.setValue(searchQuery);

    combineLatest([
      this.filter.valueChanges.pipe(startWith(this.filter.value), debounceTime(200), distinctUntilChanged())
    ])
      .pipe(map(([text]) => this.search(text)))
      .subscribe((filtered: ConsumerGroup[]) => {
        this.filteredConsumerGroups = filtered;
        this.page = 1;
      });

    this.apiService.getConsumerGroups().subscribe({ next: (res: HttpResponse<GenericResponse<ConsumerGroup[]>>) => {
        this.consumerGroups = res.body?.data ?? [];
        this.filteredConsumerGroups = this.search(this.filter.value);
        this.page = 1;
        this.errors.delete('getConsumerGroups');
        this.flags.set('getConsumerGroupsLoading',false);
      },
      error: (res:HttpErrorResponse) => {
        this.errors.set("getConsumerGroups",this.commonService.prepareError(res.error.error,'500','Failed to get consumer groups!'));
        this.flags.set('getConsumerGroupsLoading',false);
      }
    });

    this.apiService.getChart('x','q').subscribe({ next: (res: HttpResponse<GenericResponse<Chart>>) => {
          // TODO: get chart data
          },
          error: (res:HttpErrorResponse) => {
            if(res.status==428){
              this.flags.set('agentEnabled',false);
            } else {
              this.errors.set("getChart",this.commonService.prepareError(res.error.error,'500','Failed to get cluster chart information!'));
              this.flags.set('getChartLoading',false);
            }
          }
        });
  }

  search(text: string): ConsumerGroup[] {
    const term = text.toLowerCase();
    const bookmarks = this.bookmarks();
    return this.consumerGroups.filter((group: ConsumerGroup) =>
      (!this.bookmarkedOnly || bookmarks.has(group.id)) && group.id.toLowerCase().includes(term));
  }

  setBookmarkedOnly(only: boolean): void {
    this.bookmarkedOnly = only;
    this.localStorageService.setItem(BOOKMARKED_ONLY_STORAGE_KEY, only);
    this.refilter();
    this.page = 1;
  }

  isBookmarked(consumerGroup: ConsumerGroup): boolean {
    return this.bookmarks().has(consumerGroup.id);
  }

  toggleBookmark(consumerGroup: ConsumerGroup): void {
    this.errors.delete('bookmarks');
    this.bookmarkService.toggle('CONSUMER_GROUP', consumerGroup.id, res => this.bookmarkError(res, 'Failed to update the bookmark!'));
  }

  /** Recomputes the listed groups for the current search and bookmark filter, keeping the page in range. */
  private refilter(): void {
    this.filteredConsumerGroups = this.search(this.filter.value);
    this.page = Math.min(this.page, Math.max(1, Math.ceil(this.filteredConsumerGroups.length / this.pageSize)));
  }

  private bookmarkError(res: HttpErrorResponse, message: string): void {
    this.errors.set('bookmarks', this.commonService.prepareError(res.error?.error, '500', message));
  }

  /** Exports the consumer groups currently listed (search applied, all pages). */
  export(format: ExportFormat): void {
    downloadExport(this.exportContent(format, this.filteredConsumerGroups), format,
      `consumer-groups-${this.activeConnection?.name ?? 'cluster'}`);
  }

  exportContent(format: ExportFormat, consumerGroups: ConsumerGroup[]): string {
    return buildExport(format, EXPORT_FIELDS, consumerGroups, 'consumerGroups', 'consumerGroup');
  }

  /** Opens the group's details page, unless the click was on one of the row's own buttons. */
  openGroup(consumerGroup: ConsumerGroup, event: MouseEvent): void {
    if ((event.target as HTMLElement).closest('button, a')) return;
    this.router.navigate(['/consumer', consumerGroup.id]);
  }

}
