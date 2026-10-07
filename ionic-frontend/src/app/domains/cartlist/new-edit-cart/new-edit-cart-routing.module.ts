import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { NewEditCartPage } from './new-edit-cart.page';

const routes: Routes = [
  {
    path: '',
    component: NewEditCartPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class NewEditCartPageRoutingModule {}
