import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PasswordChangePage } from './password-change.page';

describe('PasswordChangePage', () => {
  let component: PasswordChangePage;
  let fixture: ComponentFixture<PasswordChangePage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(PasswordChangePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
