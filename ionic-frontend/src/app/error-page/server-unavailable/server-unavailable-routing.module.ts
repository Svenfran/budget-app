import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ServerUnavailablePage } from './server-unavailable.page';

const routes: Routes = [
  {
    path: '',
    component: ServerUnavailablePage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ServerUnavailablePageRoutingModule {}
