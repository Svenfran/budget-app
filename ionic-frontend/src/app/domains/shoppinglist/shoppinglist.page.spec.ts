import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShoppinglistPage } from './shoppinglist.page';

describe('ShoppinglistPage', () => {
  let component: ShoppinglistPage;
  let fixture: ComponentFixture<ShoppinglistPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(ShoppinglistPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
