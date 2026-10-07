import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { CartlistPage } from './cartlist.page';

const routes: Routes = [
  {
    path: '',
    component: CartlistPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CartlistPageRoutingModule {}
