import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { GroupoverviewPage } from './groupoverview.page';

const routes: Routes = [
  {
    path: '',
    component: GroupoverviewPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class GroupoverviewPageRoutingModule {}
