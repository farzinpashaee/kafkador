import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaginationComponent } from './pagination.component';

describe('PaginationComponent', () => {
  let component: PaginationComponent;
  let fixture: ComponentFixture<PaginationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginationComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PaginationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('computes total pages from item count and page size', () => {
    component.totalItems = 25;
    component.pageSize = 10;
    expect(component.totalPages).toBe(3);
  });

  it('clamps navigation to the valid page range', () => {
    component.totalItems = 25;
    component.pageSize = 10;
    component.page = 1;

    component.goTo(0);
    expect(component.page).toBe(1);

    component.goTo(99);
    expect(component.page).toBe(3);
  });

  it('emits pageChange only when the page actually changes', () => {
    component.totalItems = 25;
    component.pageSize = 10;
    component.page = 1;
    let emitted: number[] = [];
    component.pageChange.subscribe(p => emitted.push(p));

    component.goTo(1);
    expect(emitted).toEqual([]);

    component.goTo(2);
    expect(emitted).toEqual([2]);
  });
});
