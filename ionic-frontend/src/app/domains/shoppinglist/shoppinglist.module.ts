import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ShoppinglistPageRoutingModule } from './shoppinglist-routing.module';

import { ShoppinglistPage } from './shoppinglist.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ShoppinglistPageRoutingModule
  ],
  declarations: [ShoppinglistPage]
})
export class ShoppinglistPageModule {}
