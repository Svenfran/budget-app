import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { NewEditCartPageRoutingModule } from './new-edit-cart-routing.module';

import { NewEditCartPage } from './new-edit-cart.page';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    NewEditCartPageRoutingModule,
    ReactiveFormsModule,
    TranslateModule
  ],
  declarations: [NewEditCartPage]
})
export class NewEditCartPageModule {}
