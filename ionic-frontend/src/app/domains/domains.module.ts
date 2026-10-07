import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { DomainsPageRoutingModule } from './domains-routing.module';

import { DomainsPage } from './domains.page';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    TranslateModule,
    DomainsPageRoutingModule
  ],
  declarations: [DomainsPage]
})
export class DomainsPageModule {}
