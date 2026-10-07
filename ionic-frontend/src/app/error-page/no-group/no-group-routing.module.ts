import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { NoGroupPage } from './no-group.page';

const routes: Routes = [
  {
    path: '',
    component: NoGroupPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class NoGroupPageRoutingModule {}
