import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { CartlistPageRoutingModule } from './cartlist-routing.module';

import { CartlistPage } from './cartlist.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    CartlistPageRoutingModule
  ],
  declarations: [CartlistPage]
})
export class CartlistPageModule {}
