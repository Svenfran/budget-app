import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoGroupPage } from './no-group.page';

describe('NoGroupPage', () => {
  let component: NoGroupPage;
  let fixture: ComponentFixture<NoGroupPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(NoGroupPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
