import { ComponentFixture, TestBed } from '@angular/core/testing';

import { KsqlDBComponent } from './ksql-db.component';

describe('KsqlDBComponent', () => {
  let component: KsqlDBComponent;
  let fixture: ComponentFixture<KsqlDBComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KsqlDBComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(KsqlDBComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
