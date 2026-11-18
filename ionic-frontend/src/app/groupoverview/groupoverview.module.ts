import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { GroupoverviewPageRoutingModule } from './groupoverview-routing.module';

import { GroupoverviewPage } from './groupoverview.page';
import { TranslateModule } from '@ngx-translate/core';
import { GroupFormSheetComponent } from './group-form-sheet/group-form-sheet.component';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule,
    GroupoverviewPageRoutingModule,
    TranslateModule
  ],
  declarations: [GroupoverviewPage, GroupFormSheetComponent]
})
export class GroupoverviewPageModule {}
