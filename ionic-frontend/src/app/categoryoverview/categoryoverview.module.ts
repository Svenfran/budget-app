import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { CategoryoverviewPageRoutingModule } from './categoryoverview-routing.module';

import { CategoryoverviewPage } from './categoryoverview.page';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    CategoryoverviewPageRoutingModule,
    TranslateModule
  ],
  declarations: [CategoryoverviewPage]
})
export class CategoryoverviewPageModule {}
