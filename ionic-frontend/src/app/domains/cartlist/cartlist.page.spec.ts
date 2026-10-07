import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CartlistPage } from './cartlist.page';

describe('CartlistPage', () => {
  let component: CartlistPage;
  let fixture: ComponentFixture<CartlistPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(CartlistPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
