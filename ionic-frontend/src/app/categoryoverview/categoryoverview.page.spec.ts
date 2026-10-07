import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryoverviewPage } from './categoryoverview.page';

describe('CategoryoverviewPage', () => {
  let component: CategoryoverviewPage;
  let fixture: ComponentFixture<CategoryoverviewPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(CategoryoverviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
