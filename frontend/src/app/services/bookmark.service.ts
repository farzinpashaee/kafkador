import { Injectable, WritableSignal, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { BookmarkEntityType } from '../models';

/**
 * Which topics, brokers and consumer groups of the active cluster are bookmarked. Each list page
 * calls load() for its type; toggle() updates the row's icon immediately and rolls back if saving fails.
 */
@Injectable({
  providedIn: 'root'
})
export class BookmarkService {

  private apiService = inject(ApiService);

  private readonly ids: Record<BookmarkEntityType, WritableSignal<ReadonlySet<string>>> = {
    TOPIC: signal<ReadonlySet<string>>(new Set()),
    BROKER: signal<ReadonlySet<string>>(new Set()),
    CONSUMER_GROUP: signal<ReadonlySet<string>>(new Set())
  };

  /** The bookmarked ids of one type, as a signal so filtered lists can react to changes. */
  bookmarks(type: BookmarkEntityType) {
    return this.ids[type].asReadonly();
  }

  isBookmarked(type: BookmarkEntityType, id: string): boolean {
    return this.ids[type]().has(id);
  }

  load(type: BookmarkEntityType, onError?: (res: HttpErrorResponse) => void): void {
    this.apiService.getBookmarks(type).subscribe({
      next: res => this.ids[type].set(new Set((res.data ?? []).map(b => b.entityId))),
      error: (res: HttpErrorResponse) => onError?.(res)
    });
  }

  toggle(type: BookmarkEntityType, id: string, onError?: (res: HttpErrorResponse) => void): void {
    const add = !this.isBookmarked(type, id);
    this.update(type, id, add);
    const bookmark = { entityType: type, entityId: id };
    const request: Observable<unknown> = add ? this.apiService.addBookmark(bookmark) : this.apiService.removeBookmark(bookmark);
    request.subscribe({
      error: (res: HttpErrorResponse) => {
        this.update(type, id, !add);
        onError?.(res);
      }
    });
  }

  private update(type: BookmarkEntityType, id: string, bookmarked: boolean): void {
    this.ids[type].update(current => {
      const next = new Set(current);
      if (bookmarked) next.add(id); else next.delete(id);
      return next;
    });
  }

}
