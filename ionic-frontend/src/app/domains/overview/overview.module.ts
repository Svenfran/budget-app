import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { OverviewPageRoutingModule } from './overview-routing.module';

import { OverviewPage } from './overview.page';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    OverviewPageRoutingModule
  ],
  declarations: [OverviewPage, ScientificCurrencyPipe]
})
export class OverviewPageModule {}
