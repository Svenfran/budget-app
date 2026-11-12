import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ScrollingModule } from '@angular/cdk/scrolling';

import { IonicModule } from '@ionic/angular';

import { CartlistPageRoutingModule } from './cartlist-routing.module';

import { CartlistPage } from './cartlist.page';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { ExportDialogComponent } from './export-dialog/export-dialog.component';
import { FilterSheetComponent } from './filter-sheet/filter-sheet.component';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule,
    CartlistPageRoutingModule,
    ScrollingModule,
    TranslateModule,
    ScientificCurrencyPipe
  ],
  declarations: [CartlistPage, ExportDialogComponent, FilterSheetComponent]
})
export class CartlistPageModule {}
