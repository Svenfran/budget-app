import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { IonicModule, MenuController, ModalController } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { GroupFormSheetComponent } from 'src/app/groupoverview/group-form-sheet/group-form-sheet.component';
import { provideServiceTesting } from 'src/testing/test-providers';
import { NoGroupPage } from './no-group.page';

// DIV-77: „Gruppe erstellen“ öffnet das Bottom Sheet (mit Währungsauswahl) statt des Alerts
describe('NoGroupPage', () => {
  it('„Gruppe erstellen“ öffnet das Sheet „Gruppe hinzufügen“ mit der aufrufenden Seite', async () => {
    TestBed.configureTestingModule({
      declarations: [NoGroupPage],
      imports: [IonicModule.forRoot(), TranslateModule],
      providers: [
        ...provideServiceTesting(),
        { provide: MenuController, useValue: jasmine.createSpyObj('MenuController', ['enable']) },
      ],
    });
    spyOnProperty(TestBed.inject(Router), 'url').and.returnValue('/no-group');
    const modal = { present: jasmine.createSpy('present').and.resolveTo(), querySelector: () => null };
    const create = spyOn(TestBed.inject(ModalController), 'create').and.resolveTo(modal as unknown as HTMLIonModalElement);
    const fixture = TestBed.createComponent(NoGroupPage);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('ion-content ion-button') as HTMLElement).click();
    await create.calls.mostRecent().returnValue;

    const options = create.calls.mostRecent().args[0];
    expect(options.component).toBe(GroupFormSheetComponent);
    expect(options.componentProps).toEqual(jasmine.objectContaining({ mode: 'create', callingPage: '/no-group' }));
  });
});
