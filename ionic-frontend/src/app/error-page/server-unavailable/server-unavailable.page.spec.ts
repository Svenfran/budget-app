import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ServerUnavailablePage } from './server-unavailable.page';

describe('ServerUnavailablePage', () => {
  let component: ServerUnavailablePage;
  let fixture: ComponentFixture<ServerUnavailablePage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(ServerUnavailablePage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
