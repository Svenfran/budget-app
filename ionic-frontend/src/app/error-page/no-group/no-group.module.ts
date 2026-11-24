import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { NoGroupPageRoutingModule } from './no-group-routing.module';

import { NoGroupPage } from './no-group.page';
import { TranslateModule } from '@ngx-translate/core';
import { GroupFormSheetModule } from 'src/app/groupoverview/group-form-sheet/group-form-sheet.module';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    NoGroupPageRoutingModule,
    TranslateModule,
    GroupFormSheetModule
  ],
  declarations: [NoGroupPage]
})
export class NoGroupPageModule {}
