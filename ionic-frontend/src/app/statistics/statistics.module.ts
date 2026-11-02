import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { StatisticsPageRoutingModule } from './statistics-routing.module';

import { StatisticsPage } from './statistics.page';
import { TranslateModule } from '@ngx-translate/core';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { MonthNamePipe } from 'src/app/pipe/month-name.pipe';
import { BarChartComponent } from './components/bar-chart/bar-chart.component';
import { DonutChartComponent } from './components/donut-chart/donut-chart.component';

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
  declarations: [StatisticsPage, BarChartComponent, DonutChartComponent]
})
export class StatisticsPageModule {}
