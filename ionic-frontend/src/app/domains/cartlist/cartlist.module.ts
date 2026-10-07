import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ScrollingModule } from '@angular/cdk/scrolling';

import { IonicModule } from '@ionic/angular';

import { CartlistPageRoutingModule } from './cartlist-routing.module';

import { CartlistPage } from './cartlist.page';
import { ScientificCurrencyPipe } from 'src/app/pipe/scientific.pipe';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    CartlistPageRoutingModule,
    ScrollingModule,
    TranslateModule,
    ScientificCurrencyPipe
  ],
  declarations: [CartlistPage]
})
export class CartlistPageModule {}
