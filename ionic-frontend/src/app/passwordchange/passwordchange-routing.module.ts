import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { PasswordchangePage } from './passwordchange.page';

const routes: Routes = [
  {
    path: '',
    component: PasswordchangePage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PasswordchangePageRoutingModule {}
