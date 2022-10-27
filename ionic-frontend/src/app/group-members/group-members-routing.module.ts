import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { GroupMembersPage } from './group-members.page';

const routes: Routes = [
  {
    path: '',
    component: GroupMembersPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class GroupMembersPageRoutingModule {}
