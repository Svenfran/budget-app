import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { SettlementPaymentPageRoutingModule } from './settlement-payment-routing.module';

import { SettlementPaymentPage } from './settlement-payment.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ReactiveFormsModule,
    SettlementPaymentPageRoutingModule
  ],
  declarations: [SettlementPaymentPage]
})
export class SettlementPaymentPageModule {}
