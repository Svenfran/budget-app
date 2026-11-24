import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { GroupFormSheetComponent } from './group-form-sheet.component';

/**
 * Sheet „Gruppe hinzufügen / bearbeiten“ als eigenes Modul (DIV-77): Es wird aus der Gruppenübersicht, dem Seitenmenü
 * (AppModule) und der Seite „Keine Gruppe“ geöffnet; eine Komponente darf nur in einem NgModule deklariert sein.
 */
@NgModule({
  imports: [CommonModule, ReactiveFormsModule, IonicModule, TranslateModule],
  declarations: [GroupFormSheetComponent],
  exports: [GroupFormSheetComponent],
})
export class GroupFormSheetModule {}
