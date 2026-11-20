import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { GroupoverviewPageRoutingModule } from './groupoverview-routing.module';

import { GroupoverviewPage } from './groupoverview.page';
import { TranslateModule } from '@ngx-translate/core';
import { GroupFormSheetModule } from './group-form-sheet/group-form-sheet.module';
import { MembersSheetComponent } from './members-sheet/members-sheet.component';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule,
    GroupoverviewPageRoutingModule,
    TranslateModule,
    GroupFormSheetModule
  ],
  declarations: [GroupoverviewPage, MembersSheetComponent]
})
export class GroupoverviewPageModule {}
