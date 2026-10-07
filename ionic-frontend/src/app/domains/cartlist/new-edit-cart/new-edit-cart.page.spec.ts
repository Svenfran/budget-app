import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NewEditCartPage } from './new-edit-cart.page';

describe('NewEditCartPage', () => {
  let component: NewEditCartPage;
  let fixture: ComponentFixture<NewEditCartPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(NewEditCartPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
