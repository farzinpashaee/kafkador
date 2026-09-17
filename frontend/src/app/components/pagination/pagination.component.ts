import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

const MAX_VISIBLE_PAGES = 5;

@Component({
  selector: 'app-pagination',
  imports: [CommonModule],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.scss'
})
export class PaginationComponent {

  @Input() totalItems = 0;
  @Input() pageSize = 10;
  @Input() page = 1;
  @Output() pageChange = new EventEmitter<number>();

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalItems / this.pageSize));
  }

  get rangeStart(): number {
    return this.totalItems === 0 ? 0 : (this.page - 1) * this.pageSize + 1;
  }

  get rangeEnd(): number {
    return Math.min(this.page * this.pageSize, this.totalItems);
  }

  get pages(): number[] {
    const total = this.totalPages;
    const half = Math.floor(MAX_VISIBLE_PAGES / 2);
    let start = Math.max(1, this.page - half);
    const end = Math.min(total, start + MAX_VISIBLE_PAGES - 1);
    start = Math.max(1, end - MAX_VISIBLE_PAGES + 1);
    const result: number[] = [];
    for (let p = start; p <= end; p++) result.push(p);
    return result;
  }

  goTo(target: number): void {
    const clamped = Math.min(Math.max(1, target), this.totalPages);
    if (clamped === this.page) return;
    this.page = clamped;
    this.pageChange.emit(clamped);
  }

}
