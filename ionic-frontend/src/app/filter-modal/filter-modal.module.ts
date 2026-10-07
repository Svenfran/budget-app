import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { FilterModalPageRoutingModule } from './filter-modal-routing.module';

import { FilterModalPage } from './filter-modal.page';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    FilterModalPageRoutingModule,
    TranslateModule,
    ReactiveFormsModule
  ],
  declarations: [FilterModalPage]
})
export class FilterModalPageModule {}
