import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GroupoverviewPage } from './groupoverview.page';

describe('GroupoverviewPage', () => {
  let component: GroupoverviewPage;
  let fixture: ComponentFixture<GroupoverviewPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(GroupoverviewPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
