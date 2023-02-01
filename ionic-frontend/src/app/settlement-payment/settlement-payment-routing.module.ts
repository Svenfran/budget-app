import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { SettlementPaymentPage } from './settlement-payment.page';

const routes: Routes = [
  {
    path: '',
    component: SettlementPaymentPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SettlementPaymentPageRoutingModule {}
