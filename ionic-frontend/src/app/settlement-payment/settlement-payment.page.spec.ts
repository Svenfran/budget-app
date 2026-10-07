import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SettlementPaymentPage } from './settlement-payment.page';

describe('SettlementPaymentPage', () => {
  let component: SettlementPaymentPage;
  let fixture: ComponentFixture<SettlementPaymentPage>;

  beforeEach(() => {
    fixture = TestBed.createComponent(SettlementPaymentPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
