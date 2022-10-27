import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { GroupoverviewPageRoutingModule } from './groupoverview-routing.module';

import { GroupoverviewPage } from './groupoverview.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    GroupoverviewPageRoutingModule
  ],
  declarations: [GroupoverviewPage]
})
export class GroupoverviewPageModule {}
