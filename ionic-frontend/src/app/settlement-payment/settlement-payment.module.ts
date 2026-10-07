import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { SettlementPaymentPageRoutingModule } from './settlement-payment-routing.module';

import { SettlementPaymentPage } from './settlement-payment.page';
import { DatePickerComponent } from '../components/date-picker/date-picker.component';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    SettlementPaymentPageRoutingModule,
    TranslateModule,
    ReactiveFormsModule
  ],
  declarations: [SettlementPaymentPage, DatePickerComponent]
})
export class SettlementPaymentPageModule {}
