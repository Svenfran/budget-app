import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { OverviewPageRoutingModule } from './overview-routing.module';

import { OverviewPage } from './overview.page';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { MonthNamePipe } from 'src/app/pipe/month-name.pipe';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    OverviewPageRoutingModule,
    TranslateModule,
    ScientificCurrencyPipe,
    MonthNamePipe
  ],
  declarations: [OverviewPage]
})
export class OverviewPageModule {}
