import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { StatisticsPageRoutingModule } from './statistics-routing.module';

import { StatisticsPage } from './statistics.page';
import { TranslateModule } from '@ngx-translate/core';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { MonthNamePipe } from 'src/app/pipe/month-name.pipe';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    StatisticsPageRoutingModule,
    TranslateModule,
    ScientificCurrencyPipe,
    MonthNamePipe
  ],
  declarations: [StatisticsPage]
})
export class StatisticsPageModule {}
