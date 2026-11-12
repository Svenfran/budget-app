import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';
import { DragDropModule } from '@angular/cdk/drag-drop';

import { ShoppinglistPageRoutingModule } from './shoppinglist-routing.module';

import { ShoppinglistPage } from './shoppinglist.page';
import { TranslateModule } from '@ngx-translate/core';
import { SuggestionsSheetComponent } from './suggestions-sheet/suggestions-sheet.component';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    TranslateModule,
    DragDropModule,
    ShoppinglistPageRoutingModule
  ],
  declarations: [ShoppinglistPage, SuggestionsSheetComponent]
})
export class ShoppinglistPageModule {}
