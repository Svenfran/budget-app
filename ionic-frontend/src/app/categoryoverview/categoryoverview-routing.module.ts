import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { CategoryoverviewPage } from './categoryoverview.page';

const routes: Routes = [
  {
    path: '',
    component: CategoryoverviewPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class CategoryoverviewPageRoutingModule {}
