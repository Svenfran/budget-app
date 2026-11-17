import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from 'src/app/auth/auth.service';
import { User } from 'src/app/auth/user';
import { AlertService } from 'src/app/service/alert.service';
import { GroupService } from 'src/app/service/group.service';
import { StorageService } from 'src/app/service/storage.service';
import { aLoggedInUser } from 'src/testing/test-data';
import { alertServiceSpy, authServiceStub, provideServiceTesting, storageServiceFake } from 'src/testing/test-providers';
import { GroupmembersPage } from './groupmembers.page';

// DIV-60: Das Modal lädt die Mitglieder nur bei angemeldetem Nutzer und nur, solange es offen ist.
describe('GroupmembersPage – Laden der Mitglieder (DIV-60)', () => {
  let session: BehaviorSubject<User | null>;
  let loadMembers: jasmine.Spy;
  let fixture: ComponentFixture<GroupmembersPage>;
  const groupId = 4;

  const login = (id: number) => session.next(aLoggedInUser({ id }));
  const logout = () => session.next(null);

  beforeEach(() => {
    session = new BehaviorSubject<User | null>(null);
    const auth = authServiceStub(null);
    (Object.getOwnPropertyDescriptor(auth, 'user')!.get as jasmine.Spy).and.returnValue(session);
    TestBed.configureTestingModule({
      declarations: [GroupmembersPage],
      imports: [IonicModule.forRoot(), TranslateModule, ReactiveFormsModule],
      providers: [
        ...provideServiceTesting(),
        { provide: StorageService, useValue: storageServiceFake() },
        { provide: AuthService, useValue: auth },
        { provide: AlertService, useValue: alertServiceSpy() },
      ],
    });
    loadMembers = spyOn(TestBed.inject(GroupService), 'getGroupMembers');
    login(7);
    fixture = TestBed.createComponent(GroupmembersPage);
    fixture.componentInstance.groupId = groupId;
    fixture.detectChanges();
  });

  it('lädt die Mitglieder der übergebenen Gruppe bei angemeldetem Nutzer', () => {
    expect(loadMembers).toHaveBeenCalledOnceWith(groupId);
  });

  it('startet bei Logout keinen Request', () => {
    logout();

    expect(loadMembers).toHaveBeenCalledTimes(1);
  });

  it('lädt bei erneuter Emission eines angemeldeten Nutzers erneut', () => {
    login(7);

    expect(loadMembers).toHaveBeenCalledTimes(2);
    expect(loadMembers.calls.mostRecent().args).toEqual([groupId]);
  });

  it('lädt nach dem Schließen des Modals bei einem Login nicht mehr (Abo beendet)', () => {
    fixture.destroy();

    login(8);

    expect(loadMembers).toHaveBeenCalledTimes(1);
  });
});
